# lein-figwheel

This is an experimental fork of [lein-figwheel](https://github.com/bhauman/lein-figwheel/pull/309) originated in [PR #309](https://github.com/bhauman/lein-figwheel/pull/309).

My initial motivation was to [bring CLJS REPL functionality into DevTools javascript console](https://github.com/bhauman/lein-figwheel/pull/309) via [Dirac](https://github.com/binaryage/dirac).
One of the key Dirac features should be the ability of Dirac REPL to eval CLJS code in the context of paused javascript stack frame (the same way
as you can today in the DevTools eval javascript commands in console while paused on some breakpoint). The problem is that Figwheel client normally runs in the app's javascript context. This context is stopped when paused on a breakpoint which means Figwheel client is frozen and cannot serve Dirac
in REPL evaluations.

The new idea is to bring Figwheel client functionality in DevTools and run it in a separate context from the app itself. For this to work I will have
to break Figwheel client into two separate modules. Figwheel "client" will live inside Dirac DevTools and communicate with Figwheel server.
Figwheel "slave" will be a library injected by Dirac into app's javascript context and will be serving some tasks for client such as js reloading, css reloading. Some tasks which can or must be done "from outside" will be done by the client directly. For example javascript evaluation requests
must be done via DevTools debugger protocol to eval javascript in the debugger's context (not necessarily in global app's context).

With this new design we will be also able to bring Figwheel's HUD into DevTools and it will work regardless of paused debugger state.

## License

Copyright © 2014 Bruce Hauman

Distributed under the Eclipse Public License either version 1.0 or any later version.
