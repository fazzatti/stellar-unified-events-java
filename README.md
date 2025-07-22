# Ingesting Unified Events for a Classic Asset

Monitors unified asset events (mint, burn, clawback) for a specific asset on Stellar testnet.

## Configuration

Adjust the snippet code to define:

- **Asset**: fifo
- **Issuer**: GC66GVXUBUONBFLHFA7QBB2RU7HK3XT5AYM5ZZSIIG2XCYDGHXRDKUKE
- **Starting Ledger**: 574826 (for historical mode)

## Build

```bash
mvn clean install
```

## Usage

**Historical Mode** (processes from the ledger defined, ingesting one ledger per second ):

```bash
mvn exec:java -Dexec.mainClass="com.example.stellar.App"
```

**Real-time Streaming Mode** (processes live ledgers):

```bash
mvn exec:java -Dexec.mainClass="com.example.stellar.App" -Dexec.args="stream"
```
