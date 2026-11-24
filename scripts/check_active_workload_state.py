#!/usr/bin/env python3
from active_workload_state import visible_overlay

pkg="dev.lolokeksu.legioncontrol"
sample=f"""WINDOW MANAGER WINDOWS (dumpsys window windows)
  Window #0 Window{{123 u0 {pkg}}}:
    mAttrs={{(0,0)(56x56) ty=APPLICATION_OVERLAY}}
    mViewVisibility=0x0 mHasSurface=true
    isVisible=true
  Window #1 Window{{234 u0 other.app}}:
    mAttrs={{ty=BASE_APPLICATION}}
    mViewVisibility=0x0 mHasSurface=true
"""
assert visible_overlay(sample,pkg)
assert visible_overlay(sample.replace("APPLICATION_OVERLAY","2038"),pkg)
assert not visible_overlay(sample.replace("APPLICATION_OVERLAY","BASE_APPLICATION"),pkg)
assert not visible_overlay(sample.replace("mHasSurface=true","mHasSurface=false"),pkg)
assert not visible_overlay(sample.replace("mViewVisibility=0x0","mViewVisibility=0x8"),pkg)
assert not visible_overlay(sample.replace("isVisible=true","isVisible=false"),pkg)
assert not visible_overlay(sample.replace(pkg,pkg+".other"),pkg)
assert not visible_overlay("no windows",pkg)
print("PASS: owned visible overlay window parser fixtures")
