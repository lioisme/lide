package com.lioisme.lide.install;

interface IShizukuInstallerService {
    int installPackage(in ParcelFileDescriptor pfd, long fileSize, String installerPackageName);
    int uninstallPackage(String packageName);
    void destroy();
}
