package com.example.stellar;

import java.util.Arrays;

import org.stellar.sdk.Asset;
import org.stellar.sdk.Network;
import org.stellar.sdk.Server;
import org.stellar.sdk.StrKey;
import org.stellar.sdk.requests.EventListener;
import org.stellar.sdk.responses.LedgerResponse;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.ContractEvent;
import org.stellar.sdk.xdr.ContractEvent.ContractEventBody.ContractEventV0;
import org.stellar.sdk.xdr.OperationMetaV2;
import org.stellar.sdk.xdr.SCVal;
import org.stellar.sdk.xdr.TransactionMeta;

/**
 * Stellar Unified Asset Events Monitor Example
 * 
 * Monitors unified asset events (mint, burn, clawback) for a specific Stellar asset
 * using the Horizon API streaming functionality and V4 transaction metadata.
 * 
 * CONFIGURATION:
 * Before running, update the following constants:
 * - HORIZON_URL: Your Horizon server endpoint (must support transaction meta XDR)
 * - NETWORK: The Stellar network (TESTNET, FUTURENET, etc.)
 * - ASSET_CODE: The asset code to monitor (e.g., "USDC", "EURC")
 * - ASSET_ISSUER: The asset issuer's public key
 * 
 * USAGE:
 * 1. Start from latest ledger (real-time monitoring):
 *    mvn exec:java
 * 
 * 2. Start from specific ledger (historical + real-time):
 *    mvn exec:java -Dexec.args="593231"
 * 
 * REQUIREMENTS:
 * - Horizon server with transaction meta XDR enabled
 * - For local testing, use the Stellar CLI: `stellar container start testnet`
 * 
 * NOTES:
 * - Uses Protocol 23+ V4 transaction metadata format
 * - Includes rate limiting (200ms between ledgers) to prevent API overload
 * - Automatically handles cursor-based streaming for reliable event processing
 */



public class App {
    private static final String HORIZON_URL = "http://stellar-testnet.orb.local:8000";
    private static final Network NETWORK = Network.TESTNET;
    private static final String ASSET_CODE = "fifo";
    private static final String ASSET_ISSUER = "GC66GVXUBUONBFLHFA7QBB2RU7HK3XT5AYM5ZZSIIG2XCYDGHXRDKUKE";
    private static final Asset ASSET = Asset.create(ASSET_CODE + ":" + ASSET_ISSUER);
    
    private static final Server server = new Server(HORIZON_URL);

    public static void main(String[] args) {
        System.out.println("=== Monitoring Unified Asset Events ===");
        System.out.println("Asset: " + ASSET_CODE + " (" + ASSET_ISSUER + ")");
        
        // Parse starting ledger from args, or use "now" cursor for latest
        String cursor = "now";
        if (args.length > 0) {
            try {
                long startingLedger = Long.parseLong(args[0]);
                cursor = getCursorForLedger(startingLedger);
                System.out.println("Starting from ledger: " + startingLedger);
            } catch (NumberFormatException e) {
                System.out.println("Invalid ledger number, starting from latest");
            } catch (Exception e) {
                System.out.println("Error getting cursor for ledger, starting from latest: " + e.getMessage());
            }
        } else {
            System.out.println("Starting from latest ledger");
        }
    
        startStreaming(cursor);
    }
    
    private static String getCursorForLedger(long ledgerSequence) throws Exception {
        try {
            LedgerResponse ledger = server.ledgers().ledger(ledgerSequence);
            System.out.println("Found ledger " + ledgerSequence + " with paging token: " + ledger.getPagingToken());
            return ledger.getPagingToken();
        } catch (Exception e) {
            throw new Exception("Failed to get cursor for ledger " + ledgerSequence + ": " + e.getMessage());
        }
    }
    
    private static void startStreaming(String cursor) {
        try {
            EventListener<LedgerResponse> ledgerListener = new EventListener<LedgerResponse>() {
                @Override
                public void onEvent(LedgerResponse ledger) {
                    processLedger(ledger.getSequence());
                }

                @Override
                public void onFailure(java.util.Optional<Throwable> error,
                                      java.util.Optional<Integer> responseCode) {
                    System.err.println("Stream error: " + error.orElse(new Exception("Unknown error")));
                }
            };
            
            server.ledgers()
                    .cursor(cursor)
                    .stream(ledgerListener);
                    
        } catch (Exception e) {
            System.err.println("Streaming error: " + e.getMessage());
        }
    }
    
    private static void processLedger(long ledgerSequence) {
        try {
            System.out.println("[" + java.time.LocalTime.now() + "] PROCESSING LEDGER " + ledgerSequence + "...");

            Page<TransactionResponse> transactions = server.transactions()
                    .forLedger(ledgerSequence)
                    .execute();

            for (TransactionResponse tx : transactions.getRecords()) {
                parseResultMetaXdr(tx.getResultMetaXdr());
            }

            // Add delay between ledger processing to prevent hitting the rate limit of the API
            try {
                Thread.sleep(200); 
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing ledger: " + e.getMessage());
            
            try {
                Thread.sleep(1000); // On errors, wait longer before the next attempt
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private static void parseResultMetaXdr(String resultMetaXdr) {
        try {
            TransactionMeta txMeta = TransactionMeta.fromXdrBase64(resultMetaXdr);

            // Check for V4 transaction metadata (Protocol 23+)
            // V4 metadata is required for unified asset events
            // Earlier protocols use different versions (V0, V1, V2, V3)
            if (txMeta.getDiscriminant() == 4 && txMeta.getV4() != null) {
                for (OperationMetaV2 op : txMeta.getV4().getOperations()) {
                    parseOperation(op);
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing XDR: " + e.getMessage());
        }
    }
    
    private static void parseOperation(OperationMetaV2 op) {
        try {
            if (op.getEvents() != null && op.getEvents().length > 0) {
                String assetContractId = ASSET.getContractId(NETWORK);

                for (int i = 0; i < op.getEvents().length; i++) {
                    ContractEvent event = op.getEvents()[i];
                    String contractId = StrKey.encodeContract(event.getContractID().toXdrByteArray());

                    if (contractId.equals(assetContractId)) {
                        System.out.println("\n=> Asset event found!");
                        parseEvent(event);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing operation events: " + e.getMessage());
        }
    }
    

    private static void parseEvent(ContractEvent event) {
        ContractEventV0 eventBody = event.getBody().getV0();

        System.out.println("📋 EVENT DETAILS:");

        // Parse topics
        SCVal[] topics = eventBody.getTopics();
        System.out.println("Topics (" + topics.length + "):");
        for (int i = 0; i < topics.length; i++) {
            System.out.println("  [" + i + "] " + parseSCVal(topics[i]));
        }

        // Parse data
        SCVal data = eventBody.getData();
        System.out.println("Data: " + parseSCVal(data));

        System.out.println("---");
    }
    
    /**
     * Convert SCVal to native Java type, similar to JS SDK's scValToNative()
     * Attempts to convert smart contract values to readable native representations:
     * - void -> null
     * - u32, i32 -> Integer/Long
     * - u64, i64, u128, i128 -> Long/BigInteger  
     * - bool -> Boolean
     * - bytes -> byte array
     * - symbol -> String
     * - string -> String
     * - address -> formatted address string
     * If no conversion is available, returns the XDR value as string
     */
    private static Object scValToNative(SCVal scval) {
        switch (scval.getDiscriminant()) {
            case SCV_VOID:
                return null;
                
            case SCV_BOOL:
                return scval.getB();
                
            case SCV_U32:
                return scval.getU32().getUint32().getNumber().intValue();
                
            case SCV_I32:
                return scval.getI32().getInt32();
                
            case SCV_U64:
                return scval.getU64().getUint64().getNumber().longValue();
                
            case SCV_I64:
                return scval.getI64().getInt64();
                
            case SCV_U128:
                // For display purposes, convert to long if possible, otherwise use string
                long uHi = scval.getU128().getHi().getUint64().getNumber().longValue();
                long uLo = scval.getU128().getLo().getUint64().getNumber().longValue();
                if (uHi == 0) {
                    return uLo;
                }
                return "U128: " + ((uHi << 64) | (uLo & 0xFFFFFFFFFFFFFFFFL));
                
            case SCV_I128:
                // For display purposes, convert to long if possible, otherwise use string
                long hi = scval.getI128().getHi().getInt64();
                long lo = scval.getI128().getLo().getUint64().getNumber().longValue();
                if (hi == 0 || hi == -1) {
                    return ((hi << 64) | (lo & 0xFFFFFFFFFFFFFFFFL));
                }
                return "I128: " + ((hi << 64) | (lo & 0xFFFFFFFFFFFFFFFFL));
                
            case SCV_SYMBOL:
                return scval.getSym().getSCSymbol();
                
            case SCV_STRING:
                return scval.getStr().getSCString();
                
            case SCV_BYTES:
                return scval.getBytes().getSCBytes();
                
            case SCV_ADDRESS:
                return parseAddress(scval.getAddress());
                
            default:
                // Return XDR string representation for unknown types
                return scval.toString();
        }
    }
    
    /**
     * Format SCVal for display, wrapping the native value with type information
     */
    private static String parseSCVal(SCVal scval) {
        Object nativeValue = scValToNative(scval);
        String typeName = scval.getDiscriminant().toString().replace("SCV_", "").toLowerCase();
        
        if (nativeValue == null) {
            return "void: null";
        } else if (nativeValue instanceof byte[]) {
            return typeName + ": " + Arrays.toString((byte[]) nativeValue);
        } else if (nativeValue instanceof String && scval.getDiscriminant() == org.stellar.sdk.xdr.SCValType.SCV_ADDRESS) {
            return (String) nativeValue; // Address is already formatted
        } else {
            return typeName + ": " + nativeValue;
        }
    }
    
    private static String parseAddress(org.stellar.sdk.xdr.SCAddress address) {
        switch (address.getDiscriminant()) {
            case SC_ADDRESS_TYPE_ACCOUNT:
                try {
                    return "Account: " + StrKey.encodeEd25519PublicKey(address.getAccountId().getAccountID().getEd25519().getUint256());
                } catch (Exception e) {
                    return "Account: " + address.getAccountId().toString();
                }
                
            case SC_ADDRESS_TYPE_CONTRACT:
                try {
                    return "Contract: " + StrKey.encodeContract(address.getContractId().getContractID().getHash());
                } catch (Exception e) {
                    return "Contract: " + address.getContractId().toString();
                }
                
            default:
                return "Unknown Address: " + address.toString();
        }
    }
}