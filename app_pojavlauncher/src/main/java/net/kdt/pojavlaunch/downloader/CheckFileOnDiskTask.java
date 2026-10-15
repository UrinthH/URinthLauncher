package net.kdt.pojavlaunch.downloader;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.HashUtils;

import java.io.File;
import java.io.IOException;

public class CheckFileOnDiskTask extends DownloaderTask {
    /** How many times a file that fails verification after download is deleted and fetched again. */
    private static final int MAX_REDOWNLOADS = 3;
    private final boolean mAfterDownload;
    CheckFileOnDiskTask(TaskMetadata mMetadata, Downloader mHostDownloader) {
        super(mMetadata, mHostDownloader);
        this.mAfterDownload = false;
    }

    CheckFileOnDiskTask(TaskMetadata mMetadata, Downloader mHostDownloader, boolean mAfterDownload) {
        super(mMetadata, mHostDownloader);
        this.mAfterDownload = mAfterDownload;
    }

    @Override
    protected void performTask() throws IOException {
        boolean checkResult = checkFile();
        if(checkResult) {
            if(!mAfterDownload) mDownloader.addSize(mMetadata.size);
            mDownloader.fileComplete();
        }else if(!mAfterDownload) {
            mDownloader.submitFileForDownload(mMetadata);
        }else if(mMetadata.verifyFailures < MAX_REDOWNLOADS) {
            // A resumed, truncated or otherwise bad download can leave a corrupt file behind.
            // Delete it and download the whole file again instead of failing the launch at once.
            mMetadata.verifyFailures++;
            File localFile = mMetadata.path;
            long badLength = localFile.exists() ? localFile.length() : 0;
            if(localFile.exists() && !localFile.delete()) {
                throw new VerificationException("Failed to verify and could not delete the bad file: "
                        + describeFailure());
            }
            // The failed attempt already counted these bytes as downloaded; take them back.
            mDownloader.addSize(-badLength);
            mDownloader.submitFileForDownload(mMetadata);
        }else {
            throw new VerificationException("Failed to verify after " + MAX_REDOWNLOADS
                    + " re-downloads: " + describeFailure());
        }
    }

    /** Describe what was expected and what is actually on disk, to make bad downloads diagnosable. */
    private String describeFailure() {
        File localFile = mMetadata.path;
        String found = localFile.exists() ? localFile.length() + " bytes" : "file missing";
        return mMetadata + "\nfound on disk: " + found;
    }

    private boolean checkFile() throws IOException {
        File localFile = mMetadata.path;
        if(!localFile.exists()) return false;
        if(!LauncherPreferences.PREF_VERIFY_FILES) return true;
        if(mMetadata.size != -1) {
            if(mMetadata.size != localFile.length()) return false;
            if(LauncherPreferences.PREF_RAPID_START && !mAfterDownload) return true;
        }
        return mMetadata.sha1Hash == null || HashUtils.compareSHA1(localFile, mMetadata.sha1Hash);
    }

}
