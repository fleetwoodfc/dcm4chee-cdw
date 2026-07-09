# DICOM CDW Server — One-Page Executive Brochure

## Reliable DICOM Media Creation for Real-World Imaging Operations

**DICOM CDW Server (dcm4chee-cdw)** is a production-oriented DICOM Media Creation Management Server that helps imaging organizations automate media packaging, ISO generation, and publish workflows—while fitting into both legacy and modern infrastructure.

---

## Why It Matters

Healthcare organizations still need dependable media output for referral workflows, legal requests, cross-network exchange, and edge interoperability scenarios.  
DICOM CDW Server reduces manual effort and improves consistency through automated, queue-based processing.

---

## What It Delivers

- **DICOM media creation orchestration**
- **Automated compose → ISO → publish pipeline**
- **Template-driven label generation (XSLT/FOP)**
- **Optional PTBurn-compatible job handoff**
- **Docker-friendly deployment model**

---

## Business Outcomes

- **Lower operational overhead**  
  Replace manual, multi-step media handling with repeatable automation.

- **More consistent outputs**  
  Standardized templates and deterministic service flow reduce variability.

- **Faster implementation path**  
  Integrates with existing processes while supporting containerized rollout.

- **Reduced integration risk**  
  Share-based workflow model is straightforward for IT teams to validate.

---

## Typical Deployment Pattern

- DICOM CDW service endpoint for DICOM ingestion
- Admin/operations access via HTTP/JMX
- Mounted folders for:
  - job request files (`.jrq/.don/.err`)
  - status files
  - ISO files
  - label files
- Optional downstream robotic burn/print system

---

## Ideal Buyers / Stakeholders

- Radiology IT managers
- Imaging operations leaders
- Interoperability program owners
- Healthcare integration partners

---

## Quick Start

```bash
docker compose up -d