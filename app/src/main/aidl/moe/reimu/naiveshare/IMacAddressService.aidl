package moe.reimu.naiveshare;

interface IMacAddressService {
    void destroy() = 16777114; // Destroy method defined by Shizuku server

    void exit() = 1; // Exit method defined by user

    String getP2pMacAddress() = 2;
    String getMacAddressByName(String name) = 3;
}
