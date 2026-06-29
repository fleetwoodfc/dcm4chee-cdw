# PTPublish and PTBurn Sequence

This is the Markdown version of the focused sequence diagram for interactions between PTPublishService and PTBurn / Primera Publisher.

PlantUML source: [doc/ptpublish-ptburn-sequence.puml](/Users/daviddavies/Downloads/dcm4chee-cdw-2.17.1-src/doc/ptpublish-ptburn-sequence.puml)

For local testing without PTBurn installed, see the simulator notes in [doc/README.txt](/Users/daviddavies/Downloads/dcm4chee-cdw-2.17.1-src/doc/README.txt).

## Mermaid Sequence Diagram

```mermaid
sequenceDiagram
    participant PTP as PTPublishService
    participant Jobs as PTBurn Job Folder (ptburn/jobs)
    participant Status as PTBurn Status Folder (ptburn/status)
    participant PTBurn as PTBurn Service
    participant Robot as Primera Publisher (Burner + Printer)
    participant IsoShare as ISO Share (ptburn/iso)
    participant LabelShare as Label Share (ptburn/label)

    PTP->>PTP: Validate ISO exists
    PTP->>Status: Check PTBurn online (SystemStatus.txt + RobotList)
    PTP->>PTP: Map local paths to shared paths (ImageFile, PrintLabel)
    PTP->>Jobs: Write JobID.jrq with burn and label settings
    PTP->>PTP: Set request PENDING/QUEUED_PTPUBLISH
    PTP->>PTP: Requeue monitor check (non-blocking)

    PTBurn->>Jobs: Poll hot-folder for .jrq
    PTBurn->>Jobs: Read JobID.jrq
    PTBurn->>IsoShare: Open ISO from ImageFile
    opt PrintLabel exists
        PTBurn->>LabelShare: Open label from PrintLabel
    end
    PTBurn->>Robot: Burn disc and print label
    PTBurn->>Status: Write status updates (SystemStatus.txt, RobotName.txt)

    loop Scheduled monitor cycle
        PTP->>Jobs: Check markers (.qrj/.inp/.don/.err)
        alt Accepted
            PTP->>PTP: Set request PENDING/PTPUBLISH_ACCEPTED
        else In progress
            PTP->>PTP: Set request PENDING/PTPUBLISH_INPROGRESS
        end
        alt Success
            PTBurn->>Jobs: Write JobID.don
            PTP->>Jobs: Detect .don
            PTP->>Status: Read RobotName.txt and validate state
            PTP->>PTP: Mark request successful (DONE)
        else Failure
            PTBurn->>Jobs: Write JobID.err
            PTP->>Jobs: Detect .err
            PTP->>Status: Read RobotName.txt and extract error details
            PTP->>PTP: Mark request failure
        else Continue waiting
            PTP->>PTP: Requeue next monitor check
        else Timeout
            PTP->>PTP: Mark request failure with timeout state detail
        end
    end
```