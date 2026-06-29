README for DICOM CD Writer dcm4chee-cdw:
=================================

    dcm4chee-cdw is a Java implementation of a DICOM Media Creation Management
    Service Class Provider (SCP) according Annex S: Media Creation Management 
    Service Class of DICOM Standard Part 4, available at:
    http://medical.nema.org/dicom/2004.html
   
    The Media Creation Management Service Class defines a mechanism by which
    a Service Class User (SCU) can instruct a device to create Interchange
    Media.
    
    dcm4chee-cdw can be configured, that created media (CD-ROM) conforms to the
    IHE Portable Data for Imaging (PDI) Integration Profile. For details on
    this specification, please refer to Volume 1, Chapter 15 & Volume 3,
    Section 47 of the IHE Radiology Technical Framework, available at:
    http://www.rsna.org/IHE
        
    Layout of (optional) created web content and disk label is defined by
    XSL stylesheets and can therefore customized without modifying the
    java source.

    For local testing without PTBurn/Primera installed, the repository also
    includes a tiny PTBurn simulator at bin/ptburn-simulator.sh. It watches
    the PTBurn jobs folder and moves each job through PTBurn-like states:

        MyJob.JRQ -> MyJob.QRJ -> MyJob.INP -> MyJob.DON (or MyJob.ERR)

    A configurable delay is applied between each transition.

    Examples:

        # Normal flow ending in .DON
        CREATE_STATUS_FILES=1 RESPONSE_MODE=don \
        TRANSITION_DELAY_SECONDS=2 ./bin/ptburn-simulator.sh

        # Error flow ending in .ERR
        CREATE_STATUS_FILES=1 RESPONSE_MODE=err \
        TRANSITION_DELAY_SECONDS=2 ./bin/ptburn-simulator.sh

    Useful environment variables:

        JOBS_DIR=/path/to/jobs
        STATUS_DIR=/path/to/status
        POLL_SECONDS=1
        TRANSITION_DELAY_SECONDS=2
        COMPLETE_DELAY_SECONDS=2
        ROBOT_NAME='Disc Publisher XRP'
        CREATE_STATUS_FILES=1
        RESPONSE_MODE=don|err
        IN_PROGRESS_STATE=5
        COMPLETE_STATE=10
        FAILED_STATE=15
        ERROR_NUMBER=9001
        ERROR_TEXT='Simulated PTBurn error'

    The package also contains a command line utility acting as Media Creation
    Management Service Class Provider (SCU), to enable to evaluate dcm4chee-cdw
    without external Media Creation Management SCU.

    dcm4chee-cdw supports control of common CD/DVD Recoder, using free cdrtools
    from Joerg Schilling available at http://freshmeat.net/projects/cdrecord
    or (only for Windows available) nerocmd.exe command line utility of
    commercial Nero SDK.

    A commercial dcm4chee-cdw Media Writer plug-in to connect RIMAGE(tm) CD/DVD
    buring stations (s. http://www.rimage.com) is provided by my company
    Tiani Medgraph AG (for more information contact office@tiani.com).

ACKNOWLEDGEMENT

    This product includes software developed by the Apache Software Foundation
    (http://www.apache.org/).