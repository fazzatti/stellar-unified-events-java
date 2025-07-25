# Ingesting Unified Events for a Classic Asset

Monitors unified asset events (mint, burn, clawback) for a specific asset on Stellar testnet.

## Requirements

Make sure you are using a horizon instance with the transaction result meta XDR enabled for ingestion and also emitting native asset events. For this, enable the following flags for the captive core:

- `EMIT_CLASSIC_EVENTS=true`
- `BACKFILL_STELLAR_ASSET_EVENTS=true`

## Configuration

Adjust the `Config` file with the asset and horizon parameters:

- **ASSET_CODE**: `fifo`
- **ASSET_ISSUER**: `GC66GVXUBUONBFLHFA7QBB2RU7HK3XT5AYM5ZZSIIG2XCYDGHXRDKUKE`
- **HORIZON_URL**: `http://stellar-testnet.orb.local:8000` (SDF's public horizon instances do not provide the TransactionMeta)
- **NETWORK**: `Network.TESTNET`

## Build

```bash
mvn clean install
```

## Usage

**Start from Latest Ledger** (monitors new events in real-time):

```bash
mvn exec:java
```

**Start from Specific Ledger** (processes from a specific ledger number, then continues with real-time):

```bash
mvn exec:java -Dexec.args="624246"
```

Replace `624246` with your desired starting ledger number.
