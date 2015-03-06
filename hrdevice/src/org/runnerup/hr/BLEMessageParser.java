package org.runnerup.hr;

import android.annotation.TargetApi;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * A class that parses the BluetoothGattCharacteristic data from
 * a heart rate device
 *
 * Accepts both the Android BluetoothGattCharacteristic (API >= 18) and
 * the Samsung BLE SDK BluetoothGattCharacteristic (API == 17)
 *
 */
public class BLEMessageParser {
    final Characteristic message;
    boolean isUINT16;
    boolean hasRR;
    boolean hasEnergyExpended;
    int heartRate;
    public int[] rrIntervals = null;

    @TargetApi(18)
    public BLEMessageParser(BluetoothGattCharacteristic message) {
        this(new BLECharacteristic(message));

    }

    @TargetApi(17)
    public BLEMessageParser(com.samsung.android.sdk.bt.gatt.BluetoothGattCharacteristic message) {
        this.message = new SamsungBLECharacteristic(message);

    }

    private BLEMessageParser(Characteristic message) {
        this.message = message;
        if(message.getValue() != null) {
            int length = message.getValue().length;

            flags(message.getValue()[0]);
            int hrFormat = (isUINT16 ? message.getFORMAT_UINT16() : message.getFORMAT_UINT8());
            heartRate = message.getIntValue(hrFormat, 1);

            if (hasRR) {
                int rrOffset = 1 + (isUINT16 ? 2 : 1) + (hasEnergyExpended ? 2 : 0); // flags + hr + energy expended
                int numberOfRRFields = (length - rrOffset) / 2;

                rrIntervals = new int[numberOfRRFields];
                for(int i = 0; i < numberOfRRFields; i++){
                    rrIntervals[i] = message.getIntValue(message.getFORMAT_UINT16(), rrOffset + (i*2));
                }

            }
        }
    }

    private void flags(byte raw){
        isUINT16 = ((raw & 1) != 0);
        hasEnergyExpended = ((raw & 8) != 0);
        hasRR = ((raw & 16) != 0);

    }

    private interface Characteristic {

        int getFORMAT_UINT8();
        int getFORMAT_UINT16();

        byte[] getValue();
        Integer getIntValue(int formatType, int offset);

    }

    @TargetApi(18)
    private static class BLECharacteristic implements Characteristic {

        final BluetoothGattCharacteristic characteristic;

        public BLECharacteristic(BluetoothGattCharacteristic characteristic) {
            this.characteristic = characteristic;
        }

        @Override
        public int getFORMAT_UINT8() {
            return BluetoothGattCharacteristic.FORMAT_UINT8;
        }

        @Override
        public int getFORMAT_UINT16() {
            return BluetoothGattCharacteristic.FORMAT_UINT16;
        }

        @Override
        public byte[] getValue() {
            return characteristic.getValue();
        }

        @Override
        public Integer getIntValue(int formatType, int offset) {
            return characteristic.getIntValue(formatType,offset);
        }
    }

    @TargetApi(17)
    private static class SamsungBLECharacteristic implements Characteristic {

        final com.samsung.android.sdk.bt.gatt.BluetoothGattCharacteristic characteristic;

        public SamsungBLECharacteristic(com.samsung.android.sdk.bt.gatt.BluetoothGattCharacteristic characteristic) {
            this.characteristic = characteristic;
        }

        @Override
        public int getFORMAT_UINT8() {
            return com.samsung.android.sdk.bt.gatt.BluetoothGattCharacteristic.FORMAT_UINT8;
        }

        @Override
        public int getFORMAT_UINT16() {
            return com.samsung.android.sdk.bt.gatt.BluetoothGattCharacteristic.FORMAT_UINT16;
        }

        @Override
        public byte[] getValue() {
            return characteristic.getValue();
        }

        @Override
        public Integer getIntValue(int formatType, int offset) {
            return characteristic.getIntValue(formatType,offset);
        }
    }
}

