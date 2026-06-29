# DICOM Study to CD/DVD Publish Diagrams

This document provides a VS Code-friendly Mermaid rendering of the media creation and publishing workflow, plus a deployment view showing the container, shared folders, and PTBurn host interactions.

## Sequence Diagram

```mermaid
sequenceDiagram
    actor Modality as Modality / SCU
    participant StoreSCP as StoreSCP
    participant SpoolDir as SpoolDirService
    participant Scheduler as Scheduler
    participant Emulator as MediaCreationRequestEmulatorService
    participant QComposer as JMS Queue: MediaComposer
    participant Composer as MediaComposerService
    participant Fileset as Fileset Builder / DicomDirDOM
    participant QIso as JMS Queue: MakeIsoImage
    participant MkIso as MakeIsoImageService
    participant Mkisofs as mkisofs
    participant QPublish as JMS Queue: PTPublish
    participant Publish as PTPublishService
    participant IsoShare as ptburn/iso
    participant LabelShare as ptburn/label
    participant JobFolder as ptburn/jobs
    participant StatusFolder as ptburn/status
    participant PTBurn as PTBurn / Primera Publisher

    Modality->>StoreSCP: C-STORE study instances
    StoreSCP->>SpoolDir: Register and persist received objects
    SpoolDir-->>StoreSCP: Spool file locations
    StoreSCP-->>Modality: C-STORE-RSP success

    Scheduler->>Emulator: poll()
    Emulator->>SpoolDir: Inspect received objects and emulate request
    SpoolDir-->>Emulator: Candidate study / trigger files
    Emulator->>QComposer: Enqueue MediaCreationRequest

    QComposer->>Composer: Deliver MediaCreationRequest
    Composer->>Composer: Initialize fileset under /data/fileset/<uid>
    loop For each referenced SOP instance
        Composer->>SpoolDir: Move/copy instance into fileset tree
        SpoolDir-->>Composer: Instance available in fileset
    end
    Composer->>Fileset: Build DICOMDIR and media layout
    Fileset-->>Composer: DICOMDIR ready
    Composer->>Fileset: Create label content from study metadata
    Fileset->>LabelShare: Write label PNG
    LabelShare-->>Fileset: Shared label path available
    Composer->>QIso: Enqueue request with filesetDir and labelFile

    QIso->>MkIso: Deliver MediaCreationRequest
    MkIso->>MkIso: Resolve ISO path /data/iso/<uid>.iso
    MkIso->>Mkisofs: Generate ISO from fileset directory
    Mkisofs-->>MkIso: ISO created
    MkIso->>IsoShare: Write ISO file
    IsoShare-->>MkIso: Shared ISO path available
    MkIso->>QPublish: Enqueue request with isoImageFile and labelFile

    QPublish->>Publish: Deliver MediaCreationRequest
    Publish->>Publish: Map local paths to shared paths
    Note over Publish: ImageFile -> \\HOST\ptburn\iso\...\nPrintLabel -> \\HOST\ptburn\label\...
    Publish->>JobFolder: Write JRQ file

    PTBurn->>JobFolder: Poll for new JRQ
    PTBurn->>IsoShare: Open ISO referenced by ImageFile
    PTBurn->>LabelShare: Open label referenced by PrintLabel
    PTBurn->>PTBurn: Burn CD/DVD and print disc label
    PTBurn->>StatusFolder: Write .don / .err and status files

    Publish->>StatusFolder: Wait for completion
    StatusFolder-->>Publish: Job done or error details
    Publish->>Publish: Mark request done / failed
```

## Deployment Diagram

```mermaid
flowchart LR
    Modality[Modality / PACS SCU]

    subgraph DockerHost[Docker Host]
        subgraph Container[dcm4chee-cdw Container]
            DcmServer[DcmServer + StoreSCP]
            Spool[SpoolDirService\n/data/cdw.spool]
            Emulator[MediaCreationRequestEmulatorService]
            Composer[MediaComposerService\n/data/fileset internal only]
            MkIso[MakeIsoImageService\n/data/iso]
            Publish[PTPublishService]
            LabelGen[Label generation\n/data/label]
            JMS[(JMS Queues\nMediaComposer / MakeIsoImage / PTPublish)]
        end

        subgraph SharedFolders[Host Shared Folders]
            Iso[(ptburn/iso)]
            Label[(ptburn/label)]
            Jobs[(ptburn/jobs)]
            Status[(ptburn/status)]
        end
    end

    subgraph RobotHost[PTBurn / Primera Host]
        PTBurn[PTBurn Service / Robot Controller]
        Robot[Disc Publisher\nBurner + Printer]
    end

    Modality -->|C-STORE| DcmServer
    DcmServer --> Spool
    Emulator --> JMS
    JMS --> Composer
    Composer -->|move/copy study objects| Spool
    Composer -->|build DICOMDIR + fileset| Composer
    Composer -->|generate label PNG| LabelGen
    LabelGen --> Label
    Composer --> JMS
    JMS --> MkIso
    MkIso -->|mkisofs from internal fileset| Composer
    MkIso --> Iso
    MkIso --> JMS
    JMS --> Publish
    Publish -->|write JRQ| Jobs
    Publish -->|watch .don / .err| Status

    PTBurn -->|poll hot folder| Jobs
    PTBurn -->|read ISO| Iso
    PTBurn -->|read label| Label
    PTBurn -->|write status| Status
    PTBurn --> Robot

    NoteFileset[Fileset is internal to the container\nand is not shared to the PTBurn host]
    Composer -.-> NoteFileset
```

## Filesystem Path Mapping Diagram

```mermaid
flowchart TB
    subgraph Container[dcm4chee-cdw Container Filesystem]
        CSpool[/opt/jboss/server/default/data/cdw.spool/]
        CFileset[/opt/jboss/server/default/data/fileset/]
        CIso[/opt/jboss/server/default/data/iso/]
        CLabel[/opt/jboss/server/default/data/label/]
        CPublish[/var/lib/dcm4chee-cdw/ptburn/]
        CJobs[/var/lib/dcm4chee-cdw/ptburn/jobs/]
        CStatus[/var/lib/dcm4chee-cdw/ptburn/status/]
    end

    subgraph Host[Docker Host Paths]
        HIso[./ptburn/iso/]
        HLabel[./ptburn/label/]
        HJobs[./ptburn/jobs/]
        HStatus[./ptburn/status/]
    end

    subgraph PTBurnHost[PTBurn-visible Shared Paths]
        SIso[\\HOST\ptburn\iso\]
        SLabel[\\HOST\ptburn\label\]
        SJobs[PTBurn Job Request Folder]
        SStatus[PTBurn Status Folder]
    end

    CSpool -->|internal only| CFileset
    CFileset -->|mkisofs input| CIso
    CFileset -->|label metadata/rendering| CLabel

    CIso <-->|bind mount| HIso
    CLabel <-->|bind mount| HLabel
    CJobs <-->|bind mount| HJobs
    CStatus <-->|bind mount| HStatus

    HIso -->|shared as| SIso
    HLabel -->|shared as| SLabel
    HJobs -->|consumed by| SJobs
    HStatus -->|consumed by| SStatus

    JRQ[JRQ contents]
    JRQ -->|ImageFile| SIso
    JRQ -->|PrintLabel| SLabel

    NoteInternal[Fileset and cdw.spool remain inside the container\nso SpoolDir move operations stay on the same filesystem]
    NoteInternal -.-> CFileset

    NoteExternal[Only ISO and label artifacts are referenced in JRQ\nJobs and status remain shared for PTBurn workflow control]
    NoteExternal -.-> JRQ
```

## Notes

- The fileset directory remains internal to the container to avoid cross-filesystem move failures during media composition.
- Only the ISO and label artifacts are shared for PTBurn consumption.
- PTBurn still requires shared `jobs` and `status` folders for JRQ intake and completion signaling.
- The source PlantUML sequence version is available in [doc/dicom-study-to-cd-publish-sequence.puml](/Users/daviddavies/Downloads/dcm4chee-cdw-2.17.1-src/doc/dicom-study-to-cd-publish-sequence.puml).

## Sample JRQ Snippet

Example of the key path-mapped fields written by `PTPublishService`:

```ini
JobID = 1.2.40.0.13.1.1.1.172.19.0.2.20260627171743869.32772
ClientID = dcm4chee-cdw
Importance = NORMAL
ImageFile = \\HOST\ptburn\iso\1.2.40.0.13.1.1.1.172.19.0.2.20260627171743869.32773.iso
ImageType = MODE_1_2048
Copies = 1
VerifyDisc = YES
CloseDisc = YES
PrintLabel = \\HOST\ptburn\label\1.2.40.0.13.1.1.1.172.19.0.2.20260627171743869.32773.png
```

Path mapping intent:

- `ImageFile` points to the ISO share (`\\HOST\ptburn\iso\...`).
- `PrintLabel` points to the label share (`\\HOST\ptburn\label\...`).
- No JRQ field references the internal container fileset path.

## Sample .err Snippet

Example of a PTBurn error status file written to the shared status folder for a failed job:

```ini
JobID=1.2.40.0.13.1.1.1.172.19.0.2.20260627171743869.32772
Result=ERROR
ErrorCode=DISC_WRITE_FAILURE
ErrorText=Write verify failed at sector 183424
Timestamp=2026-06-27T17:41:53
```

Failure handling intent:

- PTBurn writes `.err` (or robot-specific status details) into the shared status folder.
- `PTPublishService` polls the status folder and treats `.err` as a failed completion.
- The media creation request is marked failed, and error details are propagated to logs/status.