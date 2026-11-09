Patch for operon-tools bin/operon (svc_unit_action): call systemctl without sudo, for system units allowed by one polkit rule
(user amrit may start/stop/restart only ghartv-web.service and ghartv-gate.service). Not applied: bin/operon belongs to the operon-tools lane.
Apply there with `patch -p1 < svc-no-sudo.patch`. Needs the polkit rule from the DECIDE 16 card to be installed first, or Start/Stop fails with a "polkit rule not installed" message.
