package com.lioisme.lide.install;

interface IDhizukuInstallerService {
    int installPackage(in ParcelFileDescriptor pfd, long fileSize, String expectedPackageName, long expectedVersionCode, String installerPackageName);
    int uninstallPackage(String packageName);
    void destroy();
}
