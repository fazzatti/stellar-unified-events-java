
package com.example.stellar;

import org.stellar.sdk.requests.EventListener;
import org.stellar.sdk.responses.LedgerResponse;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.xdr.ContractEvent;
import org.stellar.sdk.xdr.OperationMetaV2;
import org.stellar.sdk.xdr.TransactionMeta;

/**
 * Stellar Unified Asset Events Monitor
 * 
 * Monitors unified asset events (mint, burn, clawback) for a specific Stellar asset
 * using the Horizon API streaming functionality and V4 transaction metadata.
 * 
 * CONFIGURATION:
 * Set environment variables or modify Config.java:
 * - HORIZON_URL: Your Horizon server endpoint (must support transaction meta XDR)
 * - STELLAR_NETWORK: The Stellar network (testnet, futurenet, pubnet)
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
 * - Includes rate limiting to prevent API overload
 * - Automatically handles cursor-based streaming for reliable event processing
 */
public class App {
    private static final EventProcessor eventProcessor = new EventProcessor();

    public static void main(String[] args) {
        Config.printConfiguration();
        
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
            LedgerResponse ledger = Config.SERVER.ledgers().ledger(ledgerSequence);
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
            
            Config.SERVER.ledgers()
                    .cursor(cursor)
                    .stream(ledgerListener);
                    
        } catch (Exception e) {
            System.err.println("Streaming error: " + e.getMessage());
        }
    }
    
    private static void processLedger(long ledgerSequence) {
        try {
            System.out.println("[" + java.time.LocalTime.now() + "] PROCESSING LEDGER [" + ledgerSequence + "]...    SUPPLY: " + eventProcessor.getFormattedSupply());

            Page<TransactionResponse> transactions = Config.SERVER.transactions()
                    .forLedger(ledgerSequence)
                    .execute();

            for (TransactionResponse tx : transactions.getRecords()) {
                parseResultMetaXdr(tx.getResultMetaXdr());
            }

            // Add delay between ledger processing to prevent hitting the rate limit of the API
            try {
                Thread.sleep(Config.LEDGER_PROCESSING_DELAY_MS); 
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
            
        } catch (Exception e) {
            System.err.println("Error processing ledger: " + e.getMessage());
            
            try {
                Thread.sleep(Config.ERROR_RECOVERY_DELAY_MS); // On errors, wait longer before the next attempt
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
                for (int i = 0; i < op.getEvents().length; i++) {
                    ContractEvent event = op.getEvents()[i];
                    
                    // Check if this event is from our target asset contract
                    if (EventProcessor.isTargetAssetEvent(event)) {
                        eventProcessor.processEvent(event);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing operation events: " + e.getMessage());
        }
    }
}