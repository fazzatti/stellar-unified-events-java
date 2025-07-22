package com.example.stellar;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import org.stellar.sdk.Server;
import org.stellar.sdk.responses.Page;
import org.stellar.sdk.responses.TransactionResponse;
import org.stellar.sdk.responses.LedgerResponse;
import org.stellar.sdk.responses.operations.OperationResponse;
import org.stellar.sdk.requests.EventListener;

public class App {
    private static final String HORIZON_URL = "https://horizon-testnet.stellar.org";
    private static final Server server = new Server(HORIZON_URL);
    private static final Gson prettyGson = new Gson().newBuilder().setPrettyPrinting().create();
    
    private static final String ASSET_CODE = "fifo";
    private static final String ASSET_ISSUER = "GC66GVXUBUONBFLHFA7QBB2RU7HK3XT5AYM5ZZSIIG2XCYDGHXRDKUKE";
    private static final long STARTING_LEDGER = 574826;

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
                try { Thread.sleep(1000); } catch (InterruptedException ie) { break; }
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
                public void onFailure(java.util.Optional<Throwable> error, java.util.Optional<Integer> responseCode) {
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
                checkTransactionForAssetEvents(tx.getHash());
            }
        } catch (Exception e) {
        }
    }
    
    private static void checkTransactionForAssetEvents(String txHash) {
        try {
            Page<OperationResponse> operations = server.operations()
                .forTransaction(txHash)
                .execute();
            
            for (OperationResponse op : operations.getRecords()) {
                if ("invoke_host_function".equals(op.getType())) {
                    parseUnifiedAssetEvents(op, txHash);
                }
            }
        } catch (Exception e) {
        }
    }
    
    private static void parseUnifiedAssetEvents(OperationResponse operation, String txHash) {
        try {
            String operationJson = new Gson().toJson(operation);
            JsonObject operationObj = new Gson().fromJson(operationJson, JsonObject.class);
            
            if (operationObj.has("asset_balance_changes")) {
                JsonArray events = operationObj.getAsJsonArray("asset_balance_changes");
                
                for (JsonElement eventElement : events) {
                    JsonObject event = eventElement.getAsJsonObject();
                    
                    if (event.has("asset_code") && ASSET_CODE.equals(event.get("asset_code").getAsString()) &&
                        event.has("asset_issuer") && ASSET_ISSUER.equals(event.get("asset_issuer").getAsString())) {
                        
                        System.out.println("\n=> " + event.get("type").getAsString().toUpperCase() + 
                                         " event found in tx: " + txHash);
                        System.out.println(prettyGson.toJson(event) + "\n");
                    }
                }
            }
        } catch (Exception e) {
        }
    }
}