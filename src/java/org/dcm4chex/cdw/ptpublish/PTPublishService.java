/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), available at http://sourceforge.net/projects/dcm4che.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4chex.cdw.ptpublish;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;

import org.dcm4che.data.Dataset;
import org.dcm4che.dict.Tags;
import org.dcm4chex.cdw.common.ExecutionStatus;
import org.dcm4chex.cdw.common.ExecutionStatusInfo;
import org.dcm4chex.cdw.common.JMSDelegate;
import org.dcm4chex.cdw.common.MediaCreationException;
import org.dcm4chex.cdw.common.MediaCreationRequest;
import org.dcm4chex.cdw.common.MediaWriterServiceSupport;
import javax.jms.JMSException;

/**
 * Media writer service for Primera Technology PTPublish/PTBurn.
 */
public class PTPublishService extends MediaWriterServiceSupport {

    private static final int MONITOR_CONTINUE = 0;
    private static final int MONITOR_SUCCESS = 1;
    private static final int MONITOR_FAILURE = -1;

    private static final int STATUS_STATE_COMPLETE = 10;
    private static final int STATUS_STATE_ABORTED = 13;
    private static final int STATUS_STATE_FAILED = 15;

    private static final String EXT_JRQ = ".jrq";
    private static final String EXT_QRJ = ".qrj";
    private static final String EXT_INP = ".inp";
    private static final String EXT_DON = ".don";
    private static final String EXT_ERR = ".err";

    private static final String LABEL_PRINT_FIELD = "labelPrint";
    private static final String LABEL_PRINT_METHOD = "print";

    private String jobRequestFolder = "/var/lib/dcm4chee-cdw/ptburn/jobs";

    private String statusFolder = "/var/lib/dcm4chee-cdw/ptburn/status";

    private String localPathPrefix = "/opt/jboss/server/default/data";

    private String sharedPathPrefix = "\\\\HOST\\ptburn";

    private String robotName = "Disc Publisher XRP";

    private String clientId = "dcm4chee-cdw";

    private int copies = 1;

    private int burnSpeed = 0;

    private boolean verifyDisc = false;

    private boolean closeDisc = true;

    private boolean rejectIfNotBlank = true;

    private boolean deleteFiles = false;

    private int pollIntervalMs = 5000;

    private int jobTimeoutSecs = 0;

    private int importance = 4;

    private int binId = 0;

    public final String getJobRequestFolder() {
        return jobRequestFolder;
    }

    public final void setJobRequestFolder(String jobRequestFolder) {
        this.jobRequestFolder = jobRequestFolder;
    }

    public final String getStatusFolder() {
        if (statusFolder == null || statusFolder.length() == 0) {
            return jobRequestFolder;
        }
        return statusFolder;
    }

    public final void setStatusFolder(String statusFolder) {
        this.statusFolder = statusFolder;
    }

    public final String getLocalPathPrefix() {
        return localPathPrefix;
    }

    public final void setLocalPathPrefix(String localPathPrefix) {
        this.localPathPrefix = localPathPrefix;
    }

    public final String getSharedPathPrefix() {
        return sharedPathPrefix;
    }

    public final void setSharedPathPrefix(String sharedPathPrefix) {
        this.sharedPathPrefix = sharedPathPrefix;
    }

    public final String getRobotName() {
        return robotName;
    }

    public final void setRobotName(String robotName) {
        this.robotName = robotName;
    }

    public final String getClientId() {
        return clientId;
    }

    public final void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public final int getCopies() {
        return copies;
    }

    public final void setCopies(int copies) {
        if (copies < 1) {
            throw new IllegalArgumentException("copies: " + copies);
        }
        this.copies = copies;
    }

    public final int getBurnSpeed() {
        return burnSpeed;
    }

    public final void setBurnSpeed(int burnSpeed) {
        if (burnSpeed < 0) {
            throw new IllegalArgumentException("burnSpeed: " + burnSpeed);
        }
        this.burnSpeed = burnSpeed;
    }

    public final boolean isVerifyDisc() {
        return verifyDisc;
    }

    public final void setVerifyDisc(boolean verifyDisc) {
        this.verifyDisc = verifyDisc;
    }

    public final boolean isCloseDisc() {
        return closeDisc;
    }

    public final void setCloseDisc(boolean closeDisc) {
        this.closeDisc = closeDisc;
    }

    public final boolean isRejectIfNotBlank() {
        return rejectIfNotBlank;
    }

    public final void setRejectIfNotBlank(boolean rejectIfNotBlank) {
        this.rejectIfNotBlank = rejectIfNotBlank;
    }

    public final boolean isDeleteFiles() {
        return deleteFiles;
    }

    public final void setDeleteFiles(boolean deleteFiles) {
        this.deleteFiles = deleteFiles;
    }

    public final int getPollIntervalMs() {
        return pollIntervalMs;
    }

    public final void setPollIntervalMs(int pollIntervalMs) {
        if (pollIntervalMs < 500) {
            throw new IllegalArgumentException("pollIntervalMs: " + pollIntervalMs);
        }
        this.pollIntervalMs = pollIntervalMs;
    }

    public final int getJobTimeoutSecs() {
        return jobTimeoutSecs;
    }

    public final void setJobTimeoutSecs(int jobTimeoutSecs) {
        if (jobTimeoutSecs < 0) {
            throw new IllegalArgumentException("jobTimeoutSecs: " + jobTimeoutSecs);
        }
        this.jobTimeoutSecs = jobTimeoutSecs;
    }

    public final int getRetryIntervalSecs() {
        return getRetryInterval();
    }

    public final void setRetryIntervalSecs(int retryIntervalSecs) {
        setRetryInterval(retryIntervalSecs);
    }

    public final int getImportance() {
        return importance;
    }

    public final void setImportance(int importance) {
        if (importance < 0 || importance > 400) {
            throw new IllegalArgumentException("importance: " + importance);
        }
        this.importance = importance;
    }

    public final int getBinId() {
        return binId;
    }

    public final void setBinId(int binId) {
        if (binId < 0 || binId > 2) {
            throw new IllegalArgumentException("binId: " + binId);
        }
        this.binId = binId;
    }

    public boolean checkDrive() throws MediaCreationException {
        return true;
    }

    public boolean checkDisk() throws MediaCreationException {
        return true;
    }

    public boolean hasTOC() throws MediaCreationException {
        return false;
    }

    protected String[] makeBurnCmd(File isoImageFile) {
        return new String[] { isoImageFile.getAbsolutePath() };
    }

    protected String[] makeLoadCmd() {
        return new String[0];
    }

    protected String[] makeEjectCmd() {
        return new String[0];
    }

    public String getSystemStatus() {
        File file = new File(getStatusFolder(), "SystemStatus.txt");
        if (!file.exists()) {
            return "SystemStatus.txt not found at: " + file.getAbsolutePath();
        }
        try {
            return readFileAsString(file);
        } catch (IOException e) {
            return "Error reading SystemStatus.txt: " + e.getMessage();
        }
    }

    public String getRobotStatus() {
        File file = new File(getStatusFolder(), robotName + ".txt");
        if (!file.exists()) {
            return robotName + ".txt not found at: " + file.getAbsolutePath();
        }
        try {
            return readFileAsString(file);
        } catch (IOException e) {
            return "Error reading " + robotName + ".txt: " + e.getMessage();
        }
    }

    public String getJobState(String jobId) {
        if (jobId == null || jobId.trim().length() == 0) {
            return "UNKNOWN (jobId is empty)";
        }

        String normalizedJobId = normalizeJobId(jobId);
        File hotFolder = new File(jobRequestFolder);

        File donFile = firstExistingMarker(hotFolder, normalizedJobId, EXT_DON);
        if (donFile != null) {
            return "DON";
        }

        File errFile = firstExistingMarker(hotFolder, normalizedJobId, EXT_ERR);
        if (errFile != null) {
            return "ERR";
        }

        File inpFile = firstExistingMarker(hotFolder, normalizedJobId, EXT_INP);
        if (inpFile != null) {
            if (isMarkerTimedOut(inpFile)) {
                return "TIMEOUT(INP)";
            }
            return "INP";
        }

        File qrjFile = firstExistingMarker(hotFolder, normalizedJobId, EXT_QRJ);
        if (qrjFile != null) {
            if (isMarkerTimedOut(qrjFile)) {
                return "TIMEOUT(QRJ)";
            }
            return "QRJ";
        }

        File jrqFile = firstExistingMarker(hotFolder, normalizedJobId, EXT_JRQ);
        if (jrqFile != null) {
            if (isMarkerTimedOut(jrqFile)) {
                return "TIMEOUT(JRQ)";
            }
            return "JRQ";
        }

        return "UNKNOWN";
    }

    protected boolean handle(MediaCreationRequest rq, Dataset attrs)
            throws MediaCreationException, IOException {
        File isoFile = rq.getIsoImageFile();
        if (isoFile == null || !isoFile.exists()) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    "ISO image file not found: " + isoFile);
        }

        String jobId = buildJobId(rq);
        File hotFolder = new File(jobRequestFolder);
        if (!hotFolder.exists()) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    "PTBurn job request folder does not exist: " + hotFolder);
        }

        int terminalState = checkTerminalState(jobId, hotFolder, attrs, rq);
        if (terminalState == MONITOR_SUCCESS) {
            return true;
        }
        if (terminalState == MONITOR_FAILURE) {
            return false;
        }

        File jrqFile = findMarkerFile(hotFolder, jobId, EXT_JRQ);
        File qrjFile = findMarkerFile(hotFolder, jobId, EXT_QRJ);
        File inpFile = findMarkerFile(hotFolder, jobId, EXT_INP);
        boolean submitted = jrqFile.exists() || qrjFile.exists() || inpFile.exists();
        if (!submitted) {
            String onlineError = checkPTBurnOnline();
            if (onlineError != null) {
                return scheduleMonitorRetry(rq, attrs, jobId,
                        ExecutionStatusInfo.OUT_OF_SUPPLIES,
                        "PTBurn offline; submission deferred: " + onlineError);
            }
            writeJrqFile(jrqFile, jobId, isoFile, rq);
            log.info("PTPublish: submitted job " + jobId + " -> " + jrqFile);
            return scheduleMonitorRetry(rq, attrs, jobId,
                    ExecutionStatusInfo.QUEUED_PTPUBLISH,
                    "PTBurn job submitted; waiting for acceptance (.QRJ)");
        }

        if (qrjFile.exists()) {
            log.info("PTPublish: job " + jobId + " accepted by PTBurn ("
                    + qrjFile.getName() + ")");
        }
        if (inpFile.exists()) {
            log.info("PTPublish: job " + jobId + " is in progress ("
                    + inpFile.getName() + ")");
        }

        if (isMonitorTimedOut(jobId, hotFolder)) {
            String state;
            if (!qrjFile.exists() && !inpFile.exists()) {
                state = "submitted but not accepted by PTBurn (.QRJ not observed)";
            } else if (!inpFile.exists()) {
                state = "accepted by PTBurn but not yet processing (.INP not observed)";
            } else {
                state = "processing started (.INP observed) but no completion marker";
            }
            log.error("PTPublish job " + jobId + " timed out after " + jobTimeoutSecs
                    + "s (" + state + ")");
            attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.FAILURE);
            attrs.putCS(Tags.ExecutionStatusInfo, ExecutionStatusInfo.PROC_FAILURE);
            rq.writeAttributes(attrs, log);
            return false;
        }

        String nextInfo = ExecutionStatusInfo.QUEUED_PTPUBLISH;
        if (inpFile.exists()) {
            nextInfo = ExecutionStatusInfo.PTPUBLISH_INPROGRESS;
        } else if (qrjFile.exists()) {
            nextInfo = ExecutionStatusInfo.PTPUBLISH_ACCEPTED;
        }

        return scheduleMonitorRetry(rq, attrs, jobId,
                nextInfo,
                "PTBurn monitor recheck: awaiting .INP/.DON/.ERR for " + jobId);
    }

    private int checkTerminalState(String jobId, File hotFolder,
            Dataset attrs, MediaCreationRequest rq)
            throws MediaCreationException, IOException {
        File donFile = findMarkerFile(hotFolder, jobId, EXT_DON);
        if (donFile.exists()) {
            String errorMsg = checkCompletionStatus(jobId);
            if (errorMsg != null) {
                log.error("PTBurn reported failure for job " + jobId + ": " + errorMsg);
                attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.FAILURE);
                attrs.putCS(Tags.ExecutionStatusInfo, ExecutionStatusInfo.PROC_FAILURE);
                rq.writeAttributes(attrs, log);
                return MONITOR_FAILURE;
            }
            log.info("PTPublish: job " + jobId + " completed successfully");
            printLabel(rq);
            rq.copyDone(attrs, log);
            return MONITOR_SUCCESS;
        }

        File errFile = findMarkerFile(hotFolder, jobId, EXT_ERR);
        if (errFile.exists()) {
            String errorMsg = readJobErrorFromStatusFile(jobId);
            log.error("PTBurn job " + jobId + " failed: "
                    + (errorMsg != null ? errorMsg : "(see " + robotName + ".txt)"));
            attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.FAILURE);
            attrs.putCS(Tags.ExecutionStatusInfo, ExecutionStatusInfo.PROC_FAILURE);
            rq.writeAttributes(attrs, log);
            return MONITOR_FAILURE;
        }

        return MONITOR_CONTINUE;
    }

    private void writeJrqFile(File jrqFile, String jobId, File isoFile,
            MediaCreationRequest rq) throws IOException, MediaCreationException {
        PrintWriter writer = new PrintWriter(new FileWriter(jrqFile));
        try {
            writer.println("JobID = " + truncate(jobId, 32));
            writer.println("ClientID = " + truncate(clientId, 32));
            writer.println("Importance = " + importance);
            writer.println("ImageFile = " + toSharedPath(isoFile, "ImageFile"));
            writer.println("ImageType = MODE_1_2048");
            writer.println("Copies = " + copies);
            writer.println("BurnSpeed = " + burnSpeed);
            writer.println("VerifyDisc = " + (verifyDisc ? "YES" : "NO"));
            writer.println("CloseDisc = " + (closeDisc ? "YES" : "NO"));
            writer.println("RejectIfNotBlank = " + (rejectIfNotBlank ? "YES" : "NO"));
            writer.println("DeleteFiles = " + (deleteFiles ? "YES" : "NO"));
            writer.println("BinID = " + binId);

            String volumeName = getVolumeName(rq);
            if (volumeName != null && volumeName.length() > 0) {
                writer.println("VolumeName = " + truncate(volumeName, 32));
            }

            File labelFile = rq.getLabelFile();
            if (labelFile != null && labelFile.exists()) {
                writer.println("PrintLabel = " + toSharedPath(labelFile, "PrintLabel"));
            }
        } finally {
            writer.close();
        }
    }

    private String toSharedPath(File file, String key)
            throws MediaCreationException {
        if (localPathPrefix == null || localPathPrefix.length() == 0) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    key + " path mapping failed: LocalPathPrefix not configured");
        }
        if (sharedPathPrefix == null || sharedPathPrefix.length() == 0) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    key + " path mapping failed: SharedPathPrefix not configured");
        }

        String absolute = normalizePath(file.getAbsolutePath());
        String localPrefix = trimTrailingSlash(normalizePath(localPathPrefix));
        if (!(absolute.equals(localPrefix) || absolute.startsWith(localPrefix + "/"))) {
            throw new MediaCreationException(ExecutionStatusInfo.PROC_FAILURE,
                    key + " path mapping failed: " + file.getAbsolutePath()
                            + " is outside LocalPathPrefix=" + localPathPrefix);
        }

        String relative = absolute.length() == localPrefix.length()
                ? ""
                : absolute.substring(localPrefix.length() + 1);
        if (relative.length() == 0) {
            return sharedPathPrefix;
        }

        boolean windowsShare = sharedPathPrefix.indexOf('\\') != -1;
        String sharedRelative = windowsShare
                ? relative.replace('/', '\\')
                : relative;
        if (sharedPathPrefix.endsWith("\\") || sharedPathPrefix.endsWith("/")) {
            return sharedPathPrefix + sharedRelative;
        }
        return sharedPathPrefix + (windowsShare ? "\\" : "/") + sharedRelative;
    }

    private static String normalizePath(String path) {
        return path.replace('\\', '/');
    }

    private static String trimTrailingSlash(String path) {
        while (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String checkPTBurnOnline() {
        File statusDir = new File(getStatusFolder());
        if (!statusDir.exists() || !statusDir.isDirectory()) {
            return "status folder not available: " + statusDir.getAbsolutePath();
        }

        File systemStatusFile = new File(statusDir, "SystemStatus.txt");
        if (!systemStatusFile.exists()) {
            return "SystemStatus.txt not found at " + systemStatusFile.getAbsolutePath();
        }

        String systemStatus;
        try {
            systemStatus = readFileAsString(systemStatusFile);
        } catch (IOException e) {
            return "cannot read SystemStatus.txt: " + e.getMessage();
        }

        // The [RobotList] section lists robots as values: Robot0=<name>
        // A named section [<robotName>] is also present when the robot is online.
        // Accept either form so the check works regardless of PTBurn SDK version.
        boolean robotListed = systemStatus.indexOf("=" + robotName) >= 0
                || systemStatus.indexOf("[" + robotName + "]") >= 0;
        if (systemStatus.indexOf("[RobotList]") < 0 || !robotListed) {
            return "robot not available: " + robotName
                    + " not listed in SystemStatus.txt";
        }

        return null;
    }
    private boolean scheduleMonitorRetry(MediaCreationRequest rq,
            Dataset attrs, String jobId, String statusInfo, String reason)
            throws IOException {
        attrs.putCS(Tags.ExecutionStatus, ExecutionStatus.PENDING);
        attrs.putCS(Tags.ExecutionStatusInfo, statusInfo);
        rq.writeAttributes(attrs, log);

        try {
            JMSDelegate.queue(rq.getMediaWriterName(),
                    "Schedule PTBurn monitor for " + jobId + " - " + reason,
                    log,
                    rq,
                    System.currentTimeMillis() + pollIntervalMs);
        } catch (JMSException e) {
            throw new IOException("Failed to schedule PTBurn monitor recheck", e);
        }
        return false;
    }

    private boolean isMonitorTimedOut(String jobId, File hotFolder) {
        if (jobTimeoutSecs <= 0) {
            return false;
        }
        File marker = firstExistingMarker(hotFolder, jobId, EXT_JRQ);
        if (marker == null) {
            marker = firstExistingMarker(hotFolder, jobId, EXT_QRJ);
        }
        if (marker == null) {
            marker = firstExistingMarker(hotFolder, jobId, EXT_INP);
        }
        if (marker == null) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - marker.lastModified();
        return ageMs > jobTimeoutSecs * 1000L;
    }

    private boolean isMarkerTimedOut(File marker) {
        if (jobTimeoutSecs <= 0 || marker == null) {
            return false;
        }
        long ageMs = System.currentTimeMillis() - marker.lastModified();
        return ageMs > jobTimeoutSecs * 1000L;
    }

    private static String normalizeJobId(String jobId) {
        String normalized = jobId.trim();
        int dot = normalized.lastIndexOf('.');
        if (dot > 0) {
            normalized = normalized.substring(0, dot);
        }
        return normalized;
    }

    private static File firstExistingMarker(File hotFolder, String jobId, String ext) {
        File lower = new File(hotFolder, jobId + ext.toLowerCase());
        if (lower.exists()) {
            return lower;
        }
        File upper = new File(hotFolder, jobId + ext.toUpperCase());
        if (upper.exists()) {
            return upper;
        }
        return null;
    }

    private static File findMarkerFile(File hotFolder, String jobId, String ext) {
        File lower = new File(hotFolder, jobId + ext.toLowerCase());
        if (lower.exists()) {
            return lower;
        }
        File upper = new File(hotFolder, jobId + ext.toUpperCase());
        if (upper.exists()) {
            return upper;
        }
        return lower;
    }

    private String checkCompletionStatus(String jobId) {
        String systemStatus = getSystemStatus();
        if (systemStatus == null || systemStatus.startsWith("SystemStatus.txt not found")
                || systemStatus.startsWith("Error reading SystemStatus.txt")) {
            return systemStatus;
        }

        String robotStatus = getRobotStatus();
        if (robotStatus == null || robotStatus.startsWith(robotName + ".txt not found")
                || robotStatus.startsWith("Error reading " + robotName + ".txt")) {
            return robotStatus;
        }

        Properties jobSection = readJobSection(jobId);
        if (jobSection == null) {
            return "job section not found in " + robotName + ".txt for job " + jobId;
        }

        String stateStr = jobSection.getProperty("CurrentStatusState");
        if (stateStr == null) {
            return "CurrentStatusState missing in " + robotName + ".txt for job " + jobId;
        }

        int state;
        try {
            state = Integer.parseInt(stateStr.trim());
        } catch (NumberFormatException e) {
            return null;
        }

        if (state == STATUS_STATE_COMPLETE) {
            return null;
        }

        if (state == STATUS_STATE_FAILED || state == STATUS_STATE_ABORTED) {
            String jobErrStr = jobSection.getProperty("JobErrorString", "");
            String jobErrNum = jobSection.getProperty("JobErrorNumber", "");
            return "state=" + state + " error=" + jobErrNum + " (" + jobErrStr + ")";
        }

        return null;
    }

    private String readJobErrorFromStatusFile(String jobId) {
        Properties jobSection = readJobSection(jobId);
        if (jobSection == null) {
            return null;
        }
        String errorString = jobSection.getProperty("JobErrorString");
        String errorNumber = jobSection.getProperty("JobErrorNumber");
        if (errorString != null || errorNumber != null) {
            return "error=" + errorNumber + " (" + errorString + ")";
        }
        return null;
    }

    private Properties readJobSection(String jobId) {
        File statusFile = new File(getStatusFolder(), robotName + ".txt");
        if (!statusFile.exists()) {
            return null;
        }

        String sectionHeader = "[" + jobId + "]";
        Properties props = new Properties();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(statusFile));
            try {
                boolean inSection = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("[")) {
                        if (inSection) {
                            break;
                        }
                        inSection = line.equalsIgnoreCase(sectionHeader);
                        continue;
                    }
                    if (inSection && line.contains("=")) {
                        int index = line.indexOf('=');
                        String key = line.substring(0, index).trim();
                        String value = line.substring(index + 1).trim();
                        props.setProperty(key, value);
                    }
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            log.warn("PTPublish: could not read status file " + statusFile + ": "
                    + e.getMessage());
            return null;
        }
        return props.isEmpty() ? null : props;
    }

    private void printLabel(MediaCreationRequest rq) {
        if (!isPrintLabel()) {
            return;
        }
        try {
            Field field = MediaWriterServiceSupport.class.getDeclaredField(LABEL_PRINT_FIELD);
            field.setAccessible(true);
            Object delegate = field.get(this);
            Method method = delegate.getClass().getMethod(LABEL_PRINT_METHOD,
                    MediaCreationRequest.class);
            method.invoke(delegate, rq);
        } catch (Exception e) {
            log.warn("Failed to print PTPublish label for " + rq, e);
        }
    }

    private static String buildJobId(MediaCreationRequest rq) {
        String uid = rq.getFilesetUID();
        if (uid == null || uid.length() == 0) {
            File requestFile = rq.getRequestFile();
            uid = requestFile != null ? requestFile.getName() : "job";
        }
        String safe = uid.replace('.', '-').replaceAll("[^A-Za-z0-9\\-_]", "");
        if (safe.length() > 30) {
            safe = safe.substring(safe.length() - 30);
        }
        return safe;
    }

    private static String getVolumeName(MediaCreationRequest rq) {
        try {
            Dataset attrs = rq.readAttributes(null);
            if (attrs == null) {
                return null;
            }
            return attrs.getString(Tags.FileSetID);
        } catch (Exception e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String readFileAsString(File file) throws IOException {
        StringBuilder sb = new StringBuilder((int) file.length());
        BufferedReader reader = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } finally {
            reader.close();
        }
        return sb.toString();
    }
}
