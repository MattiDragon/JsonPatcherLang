* Check and respect doc conditions
* Finish formatter
* Support suppressions within cli and other tools
* Make DocHolder thread safe
  * Currently, it's possible for it to return collections which get mutated by other threads