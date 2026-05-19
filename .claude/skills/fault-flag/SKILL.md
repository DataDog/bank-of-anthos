---
name: fault-flag
description: Adds injectable faults to the application, which can be controlled using environment variables.
---

This is a demo application used to demonstrate Datadog's observability capabilities. Therefore, it needs to exhibit faults which users can identify in Datadog. For example: high resource usage, errors, latency, etc.

To implement a fault, we need to do the following:
- Add environment variables which can be used to activate / configure the fault. All faults are "off" by default and the code will need an environment variable set to true in order to execute the faulty behavior. In some cases, there may be additional environment variables to further control the fault behavior, such as setting an approximate length of time for latency.
- Implement the actual fault in the code, with a switch controlled by the environment variable so that the code still operated normally when the fault is "off".
- Add documentation to a file at the service level called FAULTS.md documenting the environment variables and the fault behavior.

Note that faults should be silent in terms of logging. We want it to look like a genuine application issue, not something manufactured. So, the fault code should not emit logs UNLESS the specific fault itself calls for it.

Implement a fault based upon the following concept: $ARGUMENTS[0]