package com.example.stellar;

import org.stellar.sdk.Asset;
import org.stellar.sdk.Network;
import org.stellar.sdk.Server;

/**
 * Configuration class for Stellar Asset Event Monitor
 * Centralizes all configuration constants and environment setup
 */
public class Config {
    // Environment variables with fallback defaults
    public static final String HORIZON_URL = System.getenv().getOrDefault("HORIZON_URL", "http://stellar.orb.local:8000");
    public static final String ASSET_CODE = System.getenv().getOrDefault("ASSET_CODE", "fifo");
    public static final String ASSET_ISSUER = System.getenv().getOrDefault("ASSET_ISSUER", "GC66GVXUBUONBFLHFA7QBB2RU7HK3XT5AYM5ZZSIIG2XCYDGHXRDKUKE");
    public static final Network NETWORK = Network.TESTNET;

    // Derived configuration
    public static final Asset ASSET = Asset.create(ASSET_CODE + ":" + ASSET_ISSUER);
    public static final Server SERVER = new Server(HORIZON_URL);
    
    // Rate limiting
    public static final long LEDGER_PROCESSING_DELAY_MS = 200;
    public static final long ERROR_RECOVERY_DELAY_MS = 1000;
    
     // Display colors
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_RESET = "\u001B[0m";

    public static void printConfiguration() {
        System.out.println("\n=== Configuration ===");
        System.out.println("Horizon URL: " + ANSI_GREEN + HORIZON_URL + ANSI_RESET);
        System.out.println("Network: " + ANSI_GREEN + "testnet" + ANSI_RESET);
        System.out.println("Asset: " + ANSI_GREEN + ASSET_CODE + " (" + ASSET_ISSUER + ")" + ANSI_RESET);
        System.out.println("=====================\n");
    }
}
