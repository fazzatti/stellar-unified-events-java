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



public class App {
    private static final String HORIZON_URL = "http://stellar-testnet.orb.local:8000";
    private static final Network NETWORK = Network.TESTNET;
    private static final String ASSET_CODE = "fifo";
    private static final String ASSET_ISSUER = "GC66GVXUBUONBFLHFA7QBB2RU7HK3XT5AYM5ZZSIIG2XCYDGHXRDKUKE";
    private static final Asset ASSET = Asset.create(ASSET_CODE + ":" + ASSET_ISSUER);
    private static final long STARTING_LEDGER = 593231;
    
    private static final Server server = new Server(HORIZON_URL);

    public static void main(String[] args) {
        System.out.println("=== Monitoring Unified Asset Events ===");
        System.out.println("Asset: " + ASSET_CODE + " (" + ASSET_ISSUER + ")");
        
        boolean useStreaming = args.length > 0 && "stream".equals(args[0]);
        
        if (useStreaming) {
            System.out.println("Mode: Real-time streaming\n");
            startStreamingMode();
        } else {
            System.out.println("Mode: Historical sync from ledger " + STARTING_LEDGER);
            System.out.println("Use 'stream' argument for real-time mode\n");
            startHistoricalMode();
        }
    }
    
    private static void startHistoricalMode() {
        long ledgerSequence = STARTING_LEDGER;
        
        while (true) {
            try {
                LedgerResponse ledger = server.ledgers().ledger(ledgerSequence);
                if (ledger != null) {
                    processLedger(ledger.getSequence());
                    ledgerSequence = ledger.getSequence() + 1;
                }

                Thread.sleep(1000);
                
            } catch (Exception e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
    }
    
    private static void startStreamingMode() {
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
            
            server.ledgers().cursor("now").stream(ledgerListener);
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
        } catch (Exception e) {
            System.err.println("Error processing ledger: " + e.getMessage());
        }
    }
    
    private static void parseResultMetaXdr(String resultMetaXdr) {
        try {
            TransactionMeta txMeta = TransactionMeta.fromXdrBase64(resultMetaXdr);

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
    
    private static String parseSCVal(SCVal scval) {
        switch (scval.getDiscriminant()) {
            case SCV_SYMBOL:
                return "Symbol: " + scval.getSym().getSCSymbol();
                
            case SCV_STRING:
                return "String: " + scval.getStr().getSCString();
                
            case SCV_ADDRESS:
                return "Address: " + parseAddress(scval.getAddress());
                
            case SCV_I128:
                // Convert I128 to readable number
                long hi = scval.getI128().getHi().getInt64();
                long lo = scval.getI128().getLo().getUint64().getNumber().longValue();
                return "I128: " + ((hi << 64) | (lo & 0xFFFFFFFFFFFFFFFFL));
                
            case SCV_U128:
                // Convert U128 to readable number
                long uHi = scval.getU128().getHi().getUint64().getNumber().longValue();
                long uLo = scval.getU128().getLo().getUint64().getNumber().longValue();
                return "U128: " + ((uHi << 64) | (uLo & 0xFFFFFFFFFFFFFFFFL));
                
            case SCV_I64:
                return "I64: " + scval.getI64().getInt64();
                
            case SCV_U64:
                return "U64: " + scval.getU64().getUint64().getNumber();
                
            case SCV_I32:
                return "I32: " + scval.getI32().getInt32();
                
            case SCV_U32:
                return "U32: " + scval.getU32().getUint32().getNumber();
                
            case SCV_BOOL:
                return "Bool: " + scval.getB();
                
            case SCV_BYTES:
                return "Bytes: " + Arrays.toString(scval.getBytes().getSCBytes());
                
            default:
                return "Unknown type: " + scval.getDiscriminant() + " -> " + scval.toString();
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