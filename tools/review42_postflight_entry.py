#!/usr/bin/env python3
"""Entry for the existing Review42 postflight; conservative reporting guards.

Same APK/runtime. No claims of report repair when the report cannot be queried.
"""
import json
import sys
import review42_postflight as p

_counts = p.export_counts

def counts(data):
    result = _counts(data)
    # Match the OLD adapter's nested get exactly, including present-null fields.
    result['legacy_parseable_rows'] = sum(
        p.parse_time(row.get('event_timestamp', row.get('timestamp', row.get('created_at')))) is not None
        for row in data['events'] if isinstance(row, dict))
    return result

p.export_counts = counts
_audit = p.collection_audit

def audit(target=None, request=p.get_json):
    try:
        result = _audit(target, request)
    except (TypeError, ValueError):
        return {'schema':'ghartv.collection-postflight.v1', 'state':'REPORT_ROUTE_INVALID_AUDIT_INCOMPLETE',
                'analytics_repair_deployed':False, 'raw_rows_saved':False, 'consent_changed':False,
                'service_restarted':False, 'test_events_sent':False}
    # A missing/forbidden report-health response is NOT a known old adapter.
    if result.get('report_repair_active') is not False:
        result.pop('finding', None)
    return result

p.collection_audit = audit
_hooks = p.install_hooks

def hooks(module):
    _hooks(module)
    persist = module.persist
    def persist_checked(z=None):
        # Window visibility from an earlier step does not make a later ADB failure ready.
        if module.R.get('android_result') not in ('REUSED_NORMAL_NOVA_CODE42','OPENED_NORMAL_NOVA_CODE42'):
            module.R['status'] = 'ACTION_REQUIRED'
        persist(z)
    module.persist = persist_checked

p.install_hooks = hooks

if __name__ == '__main__':
    try:
        raise SystemExit(p.main())
    except (p.PostflightError, FileNotFoundError, PermissionError) as error:
        code = str(error) if isinstance(error, p.PostflightError) else type(error).__name__.upper()+'_EXISTING_LOCAL_INPUT_UNAVAILABLE'
        print(json.dumps({'ok':False, 'error':code, 'new_key_or_service_created':False}))
        raise SystemExit(2)
