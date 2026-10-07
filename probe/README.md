# probe/

Small drivers that read the engine's own counters. They exist because the
numbers recorded in `theory/` and in commit messages were produced by code that
lived in a temporary directory and was deleted, leaving measurements that could
not be reproduced — the failure the working rules name as "a run that was not
saved was not made".

Anything that produces a number quoted anywhere belongs here, not in `/tmp`.

These are **probes**, not experiments: they answer "is this worth installing"
and "where does the time go". Performance numbers for the paper come from
`bench.sh` on the official machine, never from here.

Build and run from the repository root:

    javac -cp src -d probe/bin probe/KMax.java
    java -cp "src:probe/bin" KMax MicroblogPCU 1,2,5,10 900
