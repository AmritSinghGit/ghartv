#!/bin/bash
# Superseded publisher: never rebuild the accepted RC5 as a different binary.
printf '%s\n' 'The exact RC5 is already advertised on update/latest.json.' 'Use GHARTV_SYNC_CURRENT_AND_REPORT.command for the current owner review.' 'This historical publisher is disabled; production was not changed.'
exit 2
