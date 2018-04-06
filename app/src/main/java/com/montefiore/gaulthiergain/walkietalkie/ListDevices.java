package com.montefiore.gaulthiergain.walkietalkie;

import com.montefiore.gaulthiergain.adhoclibrary.datalink.service.AdHocDevice;

class ListDevices extends AdHocDevice {
    ListDevices(String deviceAddress, String deviceName) {
        super(deviceAddress, deviceName, -1);
    }

    ListDevices(AdHocDevice adHocDevice) {
        super(adHocDevice.getDeviceAddress(), adHocDevice.getDeviceName(), adHocDevice.getType());
    }

    @Override
    public String toString() {
        return deviceAddress + " - " + deviceName;
    }
}
