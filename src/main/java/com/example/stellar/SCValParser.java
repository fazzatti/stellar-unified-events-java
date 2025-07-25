package com.example.stellar;

import java.util.Arrays;

import org.stellar.sdk.StrKey;
import org.stellar.sdk.xdr.SCVal;

/**
 * Utility class for parsing Stellar Smart Contract Values (SCVal)
 * Similar to JS SDK's scValToNative() functionality
 */
public class SCValParser {
    
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
    public static Object scValToNative(SCVal scval) {
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
                return ((uHi << 64) | (uLo & 0xFFFFFFFFFFFFFFFFL));
                
            case SCV_I128:
                // For display purposes, convert to long if possible, otherwise use string
                long hi = scval.getI128().getHi().getInt64();
                long lo = scval.getI128().getLo().getUint64().getNumber().longValue();
                if (hi == 0 || hi == -1) {
                    return ((hi << 64) | (lo & 0xFFFFFFFFFFFFFFFFL));
                }
                return ((hi << 64) | (lo & 0xFFFFFFFFFFFFFFFFL));
                
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
    public static String formatSCVal(SCVal scval) {
        Object nativeValue = scValToNative(scval);
        
        if (nativeValue == null) {
            return "void: null";
        } else if (nativeValue instanceof byte[]) {
            return Arrays.toString((byte[]) nativeValue);
        } else if (nativeValue instanceof String && scval.getDiscriminant() == org.stellar.sdk.xdr.SCValType.SCV_ADDRESS) {
            return (String) nativeValue; // Address is already formatted
        } else {
            return nativeValue.toString();
        }
    }
    
    /**
     * Parse Stellar address from SCAddress XDR
     */
    private static String parseAddress(org.stellar.sdk.xdr.SCAddress address) {
        switch (address.getDiscriminant()) {
            case SC_ADDRESS_TYPE_ACCOUNT:
                try {
                    return StrKey.encodeEd25519PublicKey(address.getAccountId().getAccountID().getEd25519().getUint256());
                } catch (Exception e) {
                    return address.getAccountId().toString();
                }
                
            case SC_ADDRESS_TYPE_CONTRACT:
                try {
                    return StrKey.encodeContract(address.getContractId().getContractID().getHash());
                } catch (Exception e) {
                    return address.getContractId().toString();
                }
                
            default:
                return "Unknown Address: " + address.toString();
        }
    }
}
