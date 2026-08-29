Phase 7 --- Location QR, SOS Bridge, Live Journey, and Intelligent Lost
& Found

Goal

Phase 7 connects the physical Wari route to VARI Sahayak through a
network of fixed, geo-tagged QR locations and provides a complete Lost &
Found system for locating, matching, coordinating, and reuniting lost
pilgrims.

The QR system and Lost & Found system must follow these core principles:

1.  A QR code represents a fixed physical location, never an individual
    pilgrim.
2.  QR codes must not be placed on pilgrim wristbands, clothing,
    identity cards, or personal belongings.
3.  There must be one QR code per designated physical location.
4.  The same QR code must provide all four public functions:
    -   Emergency SOS Alert
    -   Volunteer Details
    -   WhatsApp Channel
    -   Live Tracking of My Journey
5.  Scanning the QR must open a public static website.
6.  A pilgrim scanning the QR must not be sent to the volunteer
    application or volunteer dashboard.
7.  An authenticated volunteer can scan the same QR from the volunteer
    application and use the QR's location as a reference point for
    emergency and Lost & Found reporting.
8.  Lost & Found must support both Lost Person and Found Person reports.
9.  Photographs and facial similarity may be used as matching signals,
    but a photograph must never be mandatory.
10. If facial recognition fails or no photograph exists, the system must
    continue matching using other attributes.
11. The Lost & Found facial matching capability must use the same Python
    computer-vision stack defined in the project's face-matching
    service: OpenCV, DeepFace, NumPy, Flask, Flask-CORS, the existing
    database/repository layer, and python-dotenv, with the Facenet
    model.
12. OpenCV/DeepFace face matching must run server-side; raw face
    embeddings must never be trusted from the client.
13. A successful Lost Person ↔ Found Person face match must create a
    match event and notify the volunteer/reporter who submitted the
    corresponding Found Person report.
14. Facial matching is a candidate-generation signal only; it must never
    automatically declare identity or complete reunification.
15. Volunteer operations must remain offline-first; queued Lost & Found
    reports must be synchronized and matched when connectivity returns.
16. Lost & Found must provide a protected live map for authorised
    volunteers/responders.
17. The map must show active Lost and Found cases, location pins,
    profile photos where authorised, and the latest known location.
18. The system must track the volunteer/team currently responsible for a
    Found Person.
19. Volunteers must be able to coordinate with each other during a Lost
    & Found case.
20. AI/matching results are candidate recommendations only. Human
    confirmation is required before reunification.

------------------------------------------------------------------------

7.1 QR Architecture

There must be one QR code for each designated physical location along
the Wari route.

Examples of QR locations include:

-   Route checkpoints
-   Medical points
-   Volunteer/help points
-   Rest areas
-   Water points
-   Important route junctions
-   Crowd-sensitive areas
-   Emergency response points
-   Other designated route locations

The QR must never identify an individual pilgrim.

Do not create personal QR codes for pilgrims.

Do not place QR codes on:

-   Wristbands
-   Clothing
-   Identity cards
-   Bags
-   Personal belongings

The QR represents only the physical location where it is installed.

------------------------------------------------------------------------

7.2 One QR --- Four Functions

Every physical QR must provide the same four public functions.

Scanning the QR opens the public VARI Sahayak website.

The landing page must provide:

1.  Emergency SOS Alert
2.  Volunteer Details
3.  WhatsApp Channel
4.  Live Tracking of My Journey

There must not be separate physical QR codes for these functions.

The same QR is the entry point for all four.

------------------------------------------------------------------------

7.3 QR Payload

The physical QR must contain only an opaque, non-sensitive location
token.

Example:

VARI-LOC-8F72A91C

The QR must not contain:

-   Pilgrim name
-   Pilgrim ID
-   Phone number
-   Aadhaar or government identity information
-   Medical information
-   Address
-   Emergency history
-   Volunteer private information
-   Photograph
-   Facial data
-   Lost & Found information

The QR should not directly encode changing configuration such as
volunteer details or WhatsApp URLs.

Instead:

QR Token ↓ Backend / Public Location Resolver ↓ QR Location ↓ Location
Configuration

This allows the information associated with a QR location to change
without requiring the physical QR to be reprinted.

------------------------------------------------------------------------

7.4 Public QR Website

When a pilgrim, volunteer, or any other person scans the QR using a
phone camera, the QR must open a public static VARI Sahayak website.

The public website must not require installation of the volunteer
application.

Example landing page:

VARI Sahayak

You are near: Route Point 24

\[ Emergency SOS Alert \]

\[ Volunteer Details \]

\[ WhatsApp Channel \]

\[ Live Tracking of My Journey \]

The website must remain a public-facing experience.

A public QR scan must never expose:

-   Volunteer dashboard
-   Internal incident management
-   Administration
-   Private Lost & Found data
-   Internal operational maps
-   Private volunteer information

------------------------------------------------------------------------

7.5 Public QR --- Emergency SOS Alert

The first option is:

«Emergency SOS Alert»

This allows a pilgrim or other public user to report an emergency.

The form should collect the minimum useful information required to
respond.

Possible fields:

-   Emergency type
-   Person's name, if known
-   Approximate age, if relevant
-   Number of people requiring help
-   Description of the situation
-   Optional phone number
-   Optional photograph
-   Current GPS location, if browser permission is granted

The QR's location is automatically attached as the fixed reference
location.

The system must not assume the person is standing exactly at the QR.

If GPS is available, store it separately.

Example:

QR Reference Location: Route Point 24 Latitude: ... Longitude: ...

Reporter GPS: Latitude: ... Longitude: ... Accuracy: ... Timestamp: ...

Source: PUBLIC_QR

The incident must enter the existing incident pipeline:

Create Incident ↓ Prioritise ↓ Match ↓ Notify ↓ Respond ↓ Resolve

SOS incidents must enter the highest priority band according to the
existing Phase 6 rules.

------------------------------------------------------------------------

7.6 Public QR --- Volunteer Details

The second option is:

«Volunteer Details»

This page displays the public volunteer/help information associated with
the QR location.

Possible information:

-   Volunteer name
-   Volunteer role
-   Help-point name
-   Volunteer/help-point ID
-   Availability/status
-   Assigned area
-   Approved contact method

Only information explicitly configured as public may be displayed.

Do not expose:

-   Private volunteer information
-   Internal identifiers
-   Authentication information
-   Private phone numbers unless explicitly configured as public
-   Private operational information

If multiple volunteers are associated with a location, display the
relevant active help-point/team information.

------------------------------------------------------------------------

7.7 Public QR --- WhatsApp Channel

The third option is:

«WhatsApp Channel»

This opens the configured official VARI Sahayak/Wari WhatsApp channel.

The destination must be configurable.

The WhatsApp URL must not be stored inside the physical QR payload.

The website resolves the QR location and retrieves the configured
channel.

If the channel is unavailable or not configured, display a graceful
fallback instead of a broken link.

------------------------------------------------------------------------

7.8 Public QR --- Live Tracking of My Journey

The fourth option is:

«Live Tracking of My Journey»

This is a pilgrim-facing route and journey experience.

It must not open the volunteer dashboard.

The experience may display:

-   Current GPS location
-   Current route position
-   Route map
-   Journey progress
-   Direction
-   Previously crossed QR locations
-   Nearby assistance points
-   Medical points
-   Volunteer points
-   Emergency points
-   Other relevant route information

The QR establishes a known route reference point.

If the pilgrim grants browser location permission, device GPS provides a
more precise current location.

The UI must distinguish between:

QR Reference Location

and:

Current Device GPS Location

A QR must never be presented as a continuous GPS tracker.

------------------------------------------------------------------------

7.9 Volunteer QR Scanning

Volunteers use the same physical QR network.

The authenticated volunteer application must provide QR scanning.

The flow is:

Volunteer scans QR ↓ QR token resolved ↓ Fixed location identified ↓
Volunteer authenticated ↓ Volunteer actions

The volunteer can use the QR location for:

-   Emergency reporting
-   Found Person reporting
-   Lost Person reporting
-   Lost Item reporting
-   Found Item reporting

The volunteer's GPS location should also be captured where permission is
available.

------------------------------------------------------------------------

7.10 Volunteer Emergency Workflow

The volunteer emergency flow is:

Volunteer scans QR ↓ Location identified ↓ Select "Report Emergency" ↓
Enter emergency/person details ↓ Confirm location ↓ Capture GPS when
available ↓ Create SOS incident ↓ Priority engine ↓ Matching engine ↓
Notification ↓ Response ↓ Resolution

The volunteer must be able to report:

«"A person requiring help is near this QR location."»

The system should retain both:

-   QR reference location
-   Volunteer GPS location

when available.

------------------------------------------------------------------------

7.11 QR Location Data Model

Use a location-oriented QR record.

Recommended conceptual table:

"qr_locations"

id qr_token location_name description latitude longitude route_segment
location_type status public_page_enabled installed_at last_verified_at
created_at updated_at

Possible "location_type" values:

CHECKPOINT MEDICAL_POINT VOLUNTEER_POINT REST_AREA WATER_POINT
ROUTE_POINT EMERGENCY_POINT OTHER

Follow the existing project's database conventions when implementing the
final migration.

The core rule is:

«A QR record represents a physical location, never an individual
pilgrim.»

------------------------------------------------------------------------

7.12 QR Resolution and Security

QR resolution must follow:

Opaque QR Token ↓ qr_locations ↓ Fixed Geo-tagged Location ↓ Public
Location Configuration

Unknown, revoked, disabled, or malformed QR tokens must produce a clear
error.

Never guess a location.

Every QR resolution should be auditable.

Where appropriate, record:

-   QR/location ID
-   Timestamp
-   Access/source
-   Authenticated volunteer ID
-   Approximate device location when permission is available
-   Related incident/report reference

Do not log unnecessary personal information.

------------------------------------------------------------------------

7.13 Public QR Security

The public QR website must never expose:

-   Volunteer dashboard
-   Internal administration
-   Private incident lists
-   Private Lost & Found reports
-   Private volunteer information
-   Internal operational data

Public users must remain within the public website experience.

Protected volunteer functionality requires authentication.

------------------------------------------------------------------------

7.14 Lost & Found Overview

Lost & Found is a core operational capability.

It must handle the complete lifecycle of a person becoming separated
from their family/group:

Person becomes lost ↓ Lost Person report ↓ Found Person report may
appear elsewhere ↓ Matching Engine ↓ Candidate matches ↓ Volunteer
review ↓ Coordination ↓ Human confirmation ↓ Reunification ↓ Resolution

The system must support both sides independently.

Lost Person

Someone is looking for a missing child/person.

Found Person

A volunteer currently has a child/person who appears to be separated
from their family/group.

------------------------------------------------------------------------

7.15 Found Person Report

A volunteer who finds a child/person must have a prominent:

«Found Person»

action.

The report must allow the volunteer to capture:

Person information

-   Photo
-   Approximate age
-   Known name
-   Gender, where relevant and appropriate
-   Approximate height, where useful
-   Clothing description
-   Physical description
-   Language
-   Other non-sensitive identifying details
-   Condition/status
-   Additional notes

A photograph is strongly recommended but not mandatory.

A Found Person report must still be possible when:

-   The person cannot provide their name
-   No photograph is available
-   The camera is unavailable
-   Network connectivity is unavailable

------------------------------------------------------------------------

7.16 Found Person Photo

If appropriate, the volunteer can:

-   Take a photograph
-   Upload an existing photograph
-   Retake the photograph
-   Replace the photograph

The photograph should be used for Lost & Found matching and
reunification.

The system must not require a photo before allowing the report to be
submitted.

------------------------------------------------------------------------

7.17 Found Person Location

When the volunteer scans a QR before creating the report, automatically
attach:

QR Location QR Latitude QR Longitude

When available, also capture:

Volunteer GPS GPS Accuracy Timestamp

The volunteer must be able to update/correct the location.

The system must distinguish:

Fixed QR Location

from:

Latest GPS Location

and:

Last Known Location

------------------------------------------------------------------------

7.18 Found Person Current Custodian

Every Found Person case must explicitly track who is currently
responsible for the person.

Example:

Found Person ↓ Current Custodian ↓ Volunteer Team V-104 ↓ Route Point 33

Store:

-   Volunteer/team
-   Help point
-   Current QR location
-   Latest GPS location
-   Timestamp

If the person is transferred:

Volunteer V-104 ↓ Handover ↓ Volunteer V-118 ↓ New Location

Every handover must be recorded.

The current custodian must always be visible to authorised responders.

------------------------------------------------------------------------

7.19 Lost Person Report

An authorised volunteer can create:

«Lost Person»

A Lost Person report can be created with or without a photograph.

When a photo is available

Allow:

-   Parent/guardian-provided photograph
-   Existing photo upload
-   Camera capture where appropriate

When no photo is available

The report must still collect:

-   Known name
-   Approximate age
-   Gender, where relevant and appropriate
-   Clothing
-   Physical description
-   Language
-   Approximate height/appearance where useful
-   Last-known area
-   Last-known QR location
-   Last-known GPS location
-   Approximate time last seen
-   Parent/guardian name
-   Parent/guardian phone number
-   Additional information

A missing photograph must never prevent creation of the Lost Person
report.

------------------------------------------------------------------------

7.20 Parent / Guardian Reporting Scenario

Example:

A parent reaches a volunteer and reports that their child is missing.

The volunteer records:

Name: Aarav

Approximate age: 8

Last-known area: Route Point 31

Last seen: 16:20

Clothing: Yellow shirt, blue shorts

Photo: Not available

The report is created immediately.

The system then searches active Found Person reports.

------------------------------------------------------------------------

7.21A Python OpenCV / DeepFace Face Matching Service

The Lost & Found face-matching capability must reuse the same Python
computer-vision architecture and dependency set as the provided
face-matching implementation.

Required Python packages/libraries:

-   opencv-python (`cv2`) --- image decoding, grayscale conversion,
    image processing, face-region cropping, augmentation and CV
    utilities.
-   DeepFace --- face detection/representation and Facenet embeddings.
-   NumPy --- embedding vectors, averaging and cosine-distance
    calculations.
-   Flask --- HTTP microservice/API layer.
-   Flask-CORS --- controlled cross-origin access for the
    application/backend integration.
-   the existing database/repository layer --- persistence and retrieval
    of person records and face embeddings from existing project
    database.
-   python-dotenv --- loading environment configuration such as existing
    project database connection settings.
-   Python standard libraries used by the reference service: `os`,
    `base64`, `binascii`, `logging`, `datetime`.

Required model and detector configuration:

MODEL_NAME = "Facenet"

DETECTOR_BACKEND_REGISTRATION = "opencv"

DETECTOR_BACKEND_RECOGNITION = "retinaface"

MATCH_TOLERANCE = 0.40

The existing Python implementation must be adapted from student/class
recognition to Lost & Found person matching. Do not create a separate
incompatible face-recognition stack for Phase 7.

The service must provide the equivalent capabilities of:

1.  Image decoding and validation
2.  Face detection
3.  Single-face validation for reference/enrollment images
4.  Face-region cropping
5.  Face embedding generation
6.  Embedding persistence
7.  Lost Person ↔ Found Person comparison
8.  Cosine-distance based candidate ranking
9.  Match thresholding
10. Graceful handling of images with no face, multiple faces, corrupt
    images, or unsupported formats

The reference implementation currently performs defensive base64
decoding, converts images to grayscale and back to 3-channel BGR, caps
request payload size, handles database failures, and returns JSON errors
instead of exposing stack traces. Preserve these reliability principles
when adapting it to VARI Sahayak.

The client must upload photographs only. It must not submit or control
the embedding used for matching.

------------------------------------------------------------------------

7.21B Face Registration / Enrollment for Lost & Found

When a Lost Person or Found Person report contains a photograph, the
backend must process it through the Python face service.

Flow:

Photo Upload ↓ Base64 / image validation ↓ OpenCV image decode ↓ Face
detection ↓ Exactly one usable face required for face enrollment ↓ Face
crop / preprocessing ↓ DeepFace + Facenet embedding ↓ Enrollment
augmentation ↓ Average accepted embeddings ↓ Persist protected embedding
with the Lost/Found report ↓ Mark photo as face-matchable

A photograph is optional for the overall Lost & Found report.

If the photograph is absent, no embedding is created and the report
remains fully valid.

If the photograph is invalid, contains no detectable face, or contains
multiple ambiguous faces, the report must still be saved when its
non-photo fields are valid. The system must mark the photo as
unavailable for facial matching and continue with
attribute/location/time matching.

The reference Python implementation expands each accepted reference
photo into 8 synthetic variants:

-   Horizontal flip
-   +12° rotation
-   -12° rotation
-   Brightness increase
-   Brightness decrease
-   Contrast increase
-   Contrast decrease
-   Slight central crop/zoom

The original image embedding plus successfully processed augmentation
embeddings are averaged into the stored profile embedding.

This augmentation strategy should be reused for Lost & Found face
enrollment unless performance testing demonstrates a documented reason
to change it.

Never store the original photograph inside the embedding field. Store
the photograph using the project's protected media/storage convention
and store only the derived embedding/vector in the face-match record.

------------------------------------------------------------------------

7.21C Lost & Found Face Comparison

When a new Lost Person report with a usable face embedding is created,
the backend must search active Found Person reports that contain usable
face embeddings.

Required flow:

Lost Person Photo ↓ Python CV Service ↓ OpenCV + DeepFace / Facenet ↓
Lost Person Embedding ↓ Load active Found Person embeddings from
database ↓ Cosine Distance Comparison ↓ Best Candidate(s) ↓
MATCH_TOLERANCE = 0.40 ↓ Candidate Match Event ↓ Combine with non-photo
signals ↓ Volunteer Review ↓ Human Confirmation ↓ Reunification /
Resolution

The reverse flow must also be supported:

Found Person Photo ↓ Generate Found Person Embedding ↓ Search active
Lost Person embeddings ↓ Candidate Matches ↓ Combined scoring ↓ Notify
relevant reporters/responders ↓ Human review

The comparison must use server-side embeddings loaded from the database.
Never accept a client-provided embedding as authoritative.

The provided Python reference implementation uses cosine distance:

distance = 1 - cosine_similarity

A pair is eligible for the face-match signal when:

distance \<= 0.40

The existing reference service derives a user-facing confidence value
from this distance. For Phase 7, this value must be treated as a ranking
indicator, not proof of identity.

Do not describe `0.40` as a guaranteed real-world identity threshold. It
is the initial configurable engineering threshold inherited from the
provided implementation and must be validated with representative Wari
data before production use.

------------------------------------------------------------------------

7.21D Face Matching and Multi-Attribute Matching Integration

Facial similarity must be one signal inside the existing Lost & Found
matching engine.

When both sides have valid embeddings:

Face Similarity + Name Similarity + Age Compatibility + Clothing
Similarity + Language Similarity + Location Compatibility + Time
Compatibility + Route Progression ↓ Combined Candidate Score

When either side has no usable face embedding:

Face Similarity = "unavailable"

The remaining signals must still be evaluated.

A failed face-processing operation must never prevent creation of the
Lost Person or Found Person report.

Missing photo ≠ face mismatch.

No detected face ≠ person mismatch.

Multiple faces in one submitted reference image ≠ automatic rejection of
the entire report; save the report and mark the photo as unusable for
automatic face matching until a valid single-person image is supplied.

------------------------------------------------------------------------

7.21E Existing Database Schema --- No New Database Structure

Face embeddings and face-matching metadata must be stored using the
project's existing database structure only.

STRICT REQUIREMENTS:

-   Do not introduce existing project database.
-   Do not add the existing database/repository layer.
-   Do not create a new database.
-   Do not create a parallel face-recognition database.
-   Do not create a new Lost & Found database model if an existing
    Lost/Found/person record already exists.
-   Do not rename existing tables or columns.
-   Do not replace PostgreSQL/Supabase with existing project database.
-   Do not duplicate Lost Person or Found Person records into a separate
    face database.
-   Do not bypass the existing repository/data-access layer.
-   Do not expose embeddings through Android APIs or public QR pages.

The implementation must first inspect and reuse the existing schema and
identify the existing Lost & Found/person/report record and the
appropriate existing persistence mechanism for optional face-match data.

If the current schema already has a suitable
field/JSON/array/vector/blob-compatible field, use that field.

If the current schema already has an associated metadata table that is
explicitly part of the existing project schema and is designed to hold
optional person/report metadata, use that existing table.

If the existing schema has no suitable place for the embedding, do NOT
invent a existing project database structure or silently create a
parallel store. Instead, document the schema limitation and use the
project's established database migration/convention only if the project
specification explicitly permits a schema extension. The default Phase 7
rule is to reuse existing schema only.

The Python CV service is a computation service, not a replacement data
layer:

Android / Existing Backend ↓ Existing Lost/Found record + existing
storage ↓ Python CV service ↓ OpenCV + DeepFace + Facenet ↓ Embedding
result ↓ Existing repository/data-access layer ↓ Existing database
schema

The embedding format must be chosen to be compatible with the existing
database field/type. Do not force existing project database-style
document storage, BSON, ObjectId references, or a new collection design.

Only the minimum metadata required by the existing schema should be
persisted, such as:

-   Face-match availability/status
-   Model identifier
-   Embedding/vector using the existing supported field representation
-   Processing timestamp
-   Optional processing/version metadata if an existing field supports
    it

The existing report/person identifier remains the source of truth.

------------------------------------------------------------------------

7.21F Match Event and Required Notification

A successful candidate match must generate a durable Match Event rather
than only displaying a result on the current volunteer's screen.

Required scenario:

1.  Volunteer A creates a Lost Person report and uploads a photograph.
2.  The photo is processed by the Python CV service.
3.  The system searches active Found Person records.
4.  A Found Person record with a sufficiently similar face is
    identified.
5.  The system combines face similarity with available attributes,
    location and time.
6.  A candidate match is created.
7.  The system identifies the volunteer/reporter who created the Found
    Person report.
8.  That reporter receives a notification that a possible match has been
    found.
9.  The Lost Person reporter/responsible volunteer may also receive the
    candidate-match notification according to role and privacy
    permissions.
10. Both sides are taken to a protected match-review screen.
11. Human confirmation is required before the case is marked reunited.

Example notification:

"Possible Lost & Found match found"

"An active Lost Person report may match a Found Person currently
assigned to your team. Review the candidate match."

The notification must not expose unnecessary sensitive information in
lock-screen previews.

Notification delivery should use the project's existing notification
infrastructure where available, with an offline-safe in-app pending
state so a transient network failure does not silently lose the event.

Duplicate notifications for the same match candidate must be prevented
or deduplicated.

If the candidate is later rejected, subsequent matching runs may surface
a different candidate according to the configured matching policy.

------------------------------------------------------------------------

7.21G Face Matching Failure and Error Handling

The Python service and application must return controlled, user-facing
errors.

Never print:

-   Python stack traces
-   DeepFace exceptions
-   OpenCV exceptions
-   Raw Flask debug pages
-   Internal file paths
-   Embedding vectors
-   Internal service credentials

Examples of user-facing messages:

"Photo could not be processed. Please upload a clearer photo."

"No face was detected. You can continue without a photo or upload
another image."

"Multiple faces were detected. Please upload a photo containing only the
person."

"Face matching is temporarily unavailable. The report was saved and will
continue using other matching information."

"Database service is temporarily unavailable. Your report is saved
locally and will sync when connectivity returns."

The server may log technical diagnostics internally, but these must
never be rendered to the volunteer.

7.21 Lost & Found Matching Engine

The matching engine must compare active Lost Person and Found Person
reports continuously.

It must not rely exclusively on facial recognition.

Available signals may include:

1.  Photo/face similarity
2.  Name similarity
3.  Approximate age
4.  Gender, where available
5.  Clothing
6.  Physical description
7.  Language
8.  Location
9.  QR location
10. GPS location
11. Route segment
12. Route direction
13. Time last seen
14. Time found
15. Route progression
16. Other available structured attributes

The system should combine these signals into a candidate ranking.

------------------------------------------------------------------------

7.22 Facial / Photo Matching

When both reports contain usable photographs, the system must use the
Phase 7 Python OpenCV/DeepFace service for facial similarity comparison.
The service uses DeepFace with the Facenet model, OpenCV for
registration-side detection/preprocessing, RetinaFace for
recognition-side detection, NumPy for vector operations, and existing
project database for persisted embeddings.

When both reports contain usable photographs, the system may perform
face/photo similarity comparison.

Flow:

Lost Person Photo + Found Person Photo ↓ Face / Photo Comparison ↓
Similarity Score ↓ Combined with Other Signals ↓ Candidate Match

Facial similarity is a matching signal, not identity proof.

If face matching produces a weak result:

«Continue searching using other attributes.»

If no photograph exists:

«Continue using non-photo matching.»

------------------------------------------------------------------------

7.23 Multi-Attribute Matching

When facial recognition is unavailable, unsuccessful, or impossible, the
matching engine must use other attributes.

Example:

LOST PERSON

Name: Aarav Age: 8 Clothing: Yellow shirt, blue shorts Language: Marathi
Last seen: Route Point 31 Time: 16:20

FOUND PERSON

Name: Unknown Age: Approximately 8 Clothing: Yellow shirt, blue shorts
Language: Marathi Found: Route Point 33 Time: 16:45

The system should identify this as a possible candidate even without a
photograph.

Missing information must not be treated as a negative match.

For example:

Lost photo: unavailable Found photo: available

does not mean a face mismatch.

It means:

«Face comparison unavailable for this pair.»

------------------------------------------------------------------------

7.24 Attribute Search

Authorised volunteers must also be able to manually search for candidate
people.

The search can use whatever information is available.

Possible filters:

-   Name
-   Approximate age
-   Gender
-   Clothing
-   Physical description
-   Route
-   QR location
-   Time range
-   Report type
-   Status
-   Match status
-   Photograph availability

Example:

Age: 7--10 Route: Route Points 30--35 Clothing: Yellow shirt Status:
FOUND

The system returns ranked candidate reports.

Volunteers do not need to fill every field.

------------------------------------------------------------------------

7.25 Location-Aware Matching

Location must be a major matching signal.

The system should understand the Wari route as a connected sequence.

It should consider:

-   Last-known location
-   Found location
-   QR location
-   GPS location
-   Route segment
-   Route direction
-   Distance
-   Route connectivity
-   Expected movement time

Example:

Lost: Route Point 31 16:20

Found: Route Point 33 16:45

If movement between those points is plausible, increase the candidate
score.

If movement is physically implausible, reduce the candidate score.

When route-network information exists, use it instead of relying only on
straight-line geographic distance.

------------------------------------------------------------------------

7.26 Time-Aware Matching

The matching engine should compare:

-   Last-seen time
-   Found time
-   Report time
-   Route distance
-   Expected travel time

A child reported missing at 16:20 and found at 16:45 at a plausible
nearby route location should receive a stronger ranking than an
otherwise similar report that is geographically or temporally
implausible.

------------------------------------------------------------------------

7.27 Candidate Scoring

Each Lost Person ↔ Found Person pair should receive an overall candidate
score.

Example:

Face similarity: unavailable Name similarity: 0.85 Age similarity: 0.95
Clothing similarity: 0.90 Location compatibility: 0.92 Time
compatibility: 0.88 Language similarity: 1.00

Overall: HIGH CONFIDENCE

Possible confidence levels:

HIGH MEDIUM LOW

The exact scoring weights and thresholds must be configurable and
validated using realistic Wari scenarios.

Missing attributes should contribute:

«No signal available»

rather than:

«Negative match»

------------------------------------------------------------------------

7.28 Multiple Candidate Results

The system must support multiple possible candidates.

If there is no clear single match, show a ranked list.

Example:

Candidate 1 --- 89% --- HIGH Candidate 2 --- 82% --- HIGH Candidate 3
--- 77% --- MEDIUM Candidate 4 --- 68% --- LOW

This is important because multiple children may have:

-   Similar clothing
-   Similar ages
-   Similar names
-   No photographs
-   Approximate rather than exact descriptions

The system should rank candidates without hiding other plausible
results.

------------------------------------------------------------------------

7.29 Candidate Cards

Candidate results should be easy for volunteers to compare.

Example:

┌──────────────────────────────────┐ │ POSSIBLE MATCH --- HIGH
CONFIDENCE │ │ │ │ \[Profile Photo\] │ │ │ │ Name: Aarav / Unknown │ │
Age: Approximately 8 │ │ Clothing: Yellow shirt │ │ │ │ Found: Route
Point 33 │ │ Time: 16:45 │ │ │ │ With: Volunteer Team V-104 │ │
Location: Near QR-033 │ │ │ │ \[Review Match\] │ │ \[Contact Volunteer\]
│ └──────────────────────────────────┘

Only information required for identification and coordination should be
displayed.

------------------------------------------------------------------------

7.30 Match Explanation

Every candidate should explain why it was surfaced.

Example:

«Why this may be a match

✓ Approximate age is compatible ✓ Clothing is similar ✓ Found two route
points ahead ✓ Time difference is plausible ✓ Language matches ⚠ No
photograph available»

The system should make its recommendation understandable to the
volunteer.

Do not present an unexplained AI score.

------------------------------------------------------------------------

7.31 Lost & Found Live Map

Lost & Found must include a dedicated protected live map for authorised
volunteers and responders.

This is a first-class operational feature.

The map must display active Lost & Found cases geographically.

It should show relevant:

-   Lost Person pins
-   Found Person pins
-   Lost Item pins
-   Found Item pins
-   QR locations
-   Help points
-   Medical points where useful
-   

------------------------------------------------------------------------

7.31A Existing Database / Supabase Integration Rule

The face-matching feature must fit into the current VARI Sahayak
architecture without changing the established persistence technology or
schema.

The authoritative flow is:

Android Volunteer App ↓ Existing authentication/session ↓ Existing Lost
& Found repository/API ↓ Existing database schema ↕ Python Face-CV
Service ↓ OpenCV + DeepFace + Facenet ↓ Embedding returned to existing
backend ↓ Existing repository writes/updates existing record structure ↓
Existing matching/notification workflow

The Python service must never become the system of record.

Use the project's current Supabase/PostgreSQL/database conventions,
repositories, migrations and access policies exactly as they already
exist. The face-CV integration is an additional processing capability
layered onto the current Lost & Found implementation.

Before implementation, inspect the current schema and map:

-   Existing Lost Person record
-   Existing Found Person record
-   Existing person/report identifier
-   Existing photo/media reference
-   Existing status fields
-   Existing location fields
-   Existing notification/event mechanism
-   Existing repository/data-source interfaces
-   Existing database policies/RLS where applicable

Then wire the face embedding and match status into those existing
structures without creating a second persistence model.

If an existing table already stores person/report metadata, the
face-match state must live there or through an already-established
related structure. Do not create a MongoDB collection or independent
face database merely because the reference Python service used MongoDB
for its student demo.

The reference Python code's MongoDB implementation is therefore an
implementation detail to be removed/adapted, while its computer-vision
processing logic remains the reference for OpenCV, DeepFace, Facenet,
augmentation, preprocessing and cosine-distance comparison.

7.32 Phase 7 Face Matching End-to-End Acceptance Criteria

The Phase 7 implementation is considered complete only when the
following flow works end-to-end:

1.  An authenticated volunteer creates a Lost Person report.
2.  The volunteer may upload a photo, but the report remains valid
    without one.
3.  If a photo is supplied, the Android client uploads it to the
    backend.
4.  The backend sends/processes the image through the Python
    OpenCV/DeepFace face service.
5.  The service generates a Facenet embedding using the configured
    detector pipeline.
6.  The embedding is persisted securely against the Lost Person report.
7.  Active Found Person records with usable embeddings are loaded from
    existing project database.
8.  Cosine-distance comparison is executed with the initial configurable
    threshold of 0.40.
9.  Candidate results are combined with non-photo attributes,
    route/location and time compatibility.
10. The candidate result includes an understandable explanation of the
    matching signals.
11. A Match Event is persisted.
12. The volunteer/reporter responsible for the matched Found Person
    receives a "possible match found" notification.
13. The Lost Person reporter/responsible volunteer receives the
    appropriate protected notification.
14. Both parties can open the protected match-review screen.
15. No automatic reunification occurs from facial similarity alone.
16. A human volunteer/responder confirms or rejects the candidate.
17. Confirmed cases move through the existing reunification/resolution
    workflow.
18. Rejected candidates remain auditable and do not corrupt the
    underlying Lost or Found reports.
19. If face processing fails, the Lost/Found report remains usable and
    attribute/location/time matching continues.
20. If connectivity is unavailable, the report is stored offline and the
    face-processing/matching workflow is retried after synchronization.
21. Technical exceptions are logged server-side only and are never
    displayed to volunteers.
22. Embeddings are never returned to clients or public pages.
23. Unknown/invalid images, no-face images, multi-face images, oversized
    payloads, database outages and notification failures are handled
    with explicit user-facing states.

This acceptance flow must be tested with: - Lost photo + Found photo -
Lost photo + Found no photo - Lost no photo + Found photo - Neither side
has a photo - Matching faces with different lighting/angle - No-face
image - Multiple-face image - Corrupt image - Duplicate reports -
Multiple candidate matches - Match rejected by volunteer - Network loss
before sync - Notification delivery failure - Database temporarily
unavailable
