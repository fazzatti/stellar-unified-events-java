# Ingesting Unified Events for a Classic Asset

Monitors unified asset events (mint, burn, clawback) for a specific asset on Stellar testnet.

## Configuration

Adjust the snippet code to define:

- **ASSET_CODE**: `fifo`
- **ASSET_ISSUER**: `GC66GVXUBUONBFLHFA7QBB2RU7HK3XT5AYM5ZZSIIG2XCYDGHXRDKUKE`
- **HORIZON_URL**: `http://stellar-testnet.orb.local:8000` (SDF's public horizon instances do not provide the TransactionMeta)
- **NETWORK**: `Network.TESTNET`

## Build

```bash
mvn clean install
```

## Usage

Make sure you are using a horizon instance with the transaction result meta XDR enabled for ingestion.
To run a local container for testnet use the Stellar CLI:

```bash
stellar container start testnet
```

**Start from Latest Ledger** (monitors new events in real-time):

```bash
mvn exec:java
```

**Start from Specific Ledger** (processes from a specific ledger number, then continues with real-time):

```bash
mvn exec:java -Dexec.args="604790"
```

Replace `604790` with your desired starting ledger number.
