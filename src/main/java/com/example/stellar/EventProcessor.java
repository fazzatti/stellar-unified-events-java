package com.example.stellar;

import java.math.BigInteger;

import org.stellar.sdk.StrKey;
import org.stellar.sdk.xdr.ContractEvent;
import org.stellar.sdk.xdr.ContractEvent.ContractEventBody.ContractEventV0;
import org.stellar.sdk.xdr.SCVal;
import org.stellar.sdk.xdr.SCValType;
import org.stellar.sdk.xdr.SCMapEntry;
/**
 * Handles parsing and processing of Stellar asset events
 */
public class EventProcessor {
    private BigInteger supply = BigInteger.ZERO;
    
    /**
     * Process a contract event and update supply tracking
     * @param event The contract event to process
     */
    public void processEvent(ContractEvent event) {
        ContractEventV0 eventBody = event.getBody().getV0();
        
        // Parse topics
        SCVal[] rawTopics = eventBody.getTopics();
        String[] topics = new String[rawTopics.length];
        for (int i = 0; i < rawTopics.length; i++) {
            topics[i] = SCValParser.scValToNative(rawTopics[i]).toString();
        }
        
        // Parse data
        // When muxed addresses are involved, the data can be a map structure
        // This handles both simple value and map format (for muxed addresses)
        SCVal rawData = eventBody.getData();
        String data = extractAmountFromData(rawData);
        
        // Check if this is our asset's event
        String expectedAssetIdentifier = Config.ASSET_CODE + ":" + Config.ASSET_ISSUER;
        if (topics.length > 2 && topics[2].equals(expectedAssetIdentifier)) {
            processAssetEvent(topics[0], data);
        }
    }
    
    /**
     * Extract amount from event data, handling both simple values and map structures
     * @param rawData The raw SCVal data
     * @return String representation of the amount
     * 
     */
    private String extractAmountFromData(SCVal rawData) {
        // Check if data is a map (muxed address case)
        if (rawData.getDiscriminant() == SCValType.SCV_MAP && rawData.getMap() != null) {
            // Look for "amount" key in the map
            for (SCMapEntry entry : rawData.getMap().getSCMap()) {
                Object keyValue = SCValParser.scValToNative(entry.getKey());
                if ("amount".equals(keyValue.toString())) {
                    return SCValParser.scValToNative(entry.getVal()).toString();
                }
            }
            // If no "amount" key found, fallback to formatted representation
            return SCValParser.formatSCVal(rawData);
        } else {
            // Simple value case (original format)
            return SCValParser.formatSCVal(rawData);
        }
    }
    
    /**
     * Process asset-specific events (mint, burn, clawback)
     * @param eventType The type of event (mint, burn, clawback)
     * @param amountData The amount data from the event
     */
    private void processAssetEvent(String eventType, String amountData) {
        try {
            BigInteger amount = new BigInteger(amountData);
            
            switch (eventType) {
                case "mint":
                    supply = supply.add(amount);
                    System.out.println(Config.ANSI_GREEN);
                    break;
                case "burn":
                case "clawback":
                    supply = supply.subtract(amount);
                    System.out.println(Config.ANSI_RED);
                    break;
                default:
                    System.out.println("Untracked Event Type: " + eventType + "\nNo supply change.");
                    return;
            }
            
            System.out.println("(" + eventType.toUpperCase() + ")" + Config.ANSI_RESET + " -> " + amountData + "\n");
        } catch (Exception e) {
            System.err.println("Error processing asset event: " + e.getMessage());
        }
    }
    
    /**
     * Check if a contract event matches our target asset
     * @param event The contract event
     * @return true if the event is from our target asset contract
     */
    public static boolean isTargetAssetEvent(ContractEvent event) {
        try {
            String contractId = StrKey.encodeContract(event.getContractID().toXdrByteArray());
            String assetContractId = Config.ASSET.getContractId(Config.NETWORK);
            return contractId.equals(assetContractId);
        } catch (Exception e) {
            System.err.println("Error checking asset contract: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get current supply formatted for display
     * @return Formatted supply string
     */
    public String getFormattedSupply() {
        return StellarUtils.formatSupply(supply);
    }
    
}
