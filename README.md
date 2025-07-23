# Ingesting Unified Events for a Classic Asset

Monitors unified asset events (mint, burn, clawback) for a specific asset on Stellar testnet.

## Configuration

Adjust the snippet code to define:

- **ASSET_CODE**: `fifo`
- **ASSET_ISSUER**: `GC66GVXUBUONBFLHFA7QBB2RU7HK3XT5AYM5ZZSIIG2XCYDGHXRDKUKE`
- **STARTING_LEDGER**: `574826` (for historical mode)
- **HORIZON_URL**: `http://localhost:8000` (SDF's public horizon instances do not provide the TransactionMeta)
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

**Historical Mode** (processes from the ledger defined, ingesting one ledger per second ):

```bash
mvn exec:java -Dexec.mainClass="com.example.stellar.App"
```

**Real-time Streaming Mode** (processes live ledgers):

```bash
mvn exec:java -Dexec.mainClass="com.example.stellar.App" -Dexec.args="stream"
```
