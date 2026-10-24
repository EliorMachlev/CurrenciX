"""Puts each failed test (class, name, message) on the workflow run as an
error annotation, readable from the checks page and the API without the
full log. Arguments: glob patterns for JUnit XML result files. Needs
defusedxml (pip install --require-hashes -r .github/scripts/requirements.txt)."""

import glob
import sys
# defusedxml rather than xml: it refuses entity expansion and external
# entities, so a crafted results file can't read files or blow up memory.
from defusedxml import ElementTree as ET

# Long messages are cut so each annotation stays readable.
MAX_MESSAGE = 900

# A test that skipped itself (an unmet assumption, such as a benchmark on an
# emulator) isn't a failure, though some runners record it as one.
SKIPPED = "AssumptionViolatedException"


def escape(text, extra=""):
    # Workflow-command escaping: one annotation per line.
    text = text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")
    for char in extra:
        text = text.replace(char, "%{:02X}".format(ord(char)))
    return text


for pattern in sys.argv[1:]:
    for path in glob.glob(pattern, recursive=True):
        for case in ET.parse(path).getroot().iter("testcase"):
            for problem in case.findall("failure") + case.findall("error"):
                message = (problem.get("message") or problem.text or "")[:MAX_MESSAGE]
                if SKIPPED in message:
                    continue
                title = f"{case.get('classname')} > {case.get('name')}"
                print(f"::error title={escape(title, ':,')}::{escape(message)}")
