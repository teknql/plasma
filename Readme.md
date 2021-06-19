# Plasma

Web RPC for Clojure + Clojurescript.

## Status

Extremely early access. Currently being spun out from internal projects at Teknql.

APIs likely to change (sometimes drastically) and documentation not yet written.

Use at your own risk.


## What

Plasma provides a DSL to define isomorphic RPC endpoints that feel familiar to `defn` functions. It
abstracts away the need for explicit routing, serialization, session management, or resource
management. In clojure, the defined endpoints compile away into functions, making them easy to test.
On the clojurescript side they compile away into promises or streams which communicate over
websockets.


## Why

Many of the complexities in modern web development revolve around routing, serialization, session
state management and resource cleanup. Frequently these systems are built from the ground-up per
application and often feel like the "thar be dragons" part of the code base - lacking functionality
and fragily hacked together. Plasma aims to provide ergonomic and complete APIs for these
fundemental needs so that developers can spend more time building their product and less time
reinventing the wheel.

## Status and Roadmap

Plasma is in early alpha. It was spun out of yet another iteration of solving
this problem in an internal application. Any community feedback is widely
appreciated, we're interested in making Plasma as useful and as flexible as
possible.

### Planned Features

- [X] Clojure + Clojurescript RPC
- [X] Stream management
- [X] Session state management
- [X] Session-lifetime bounded resources and cleanup
- [ ] Write transports
- [ ] Compiled middleware via `defn` attribute maps.
- [ ] Abstract serialization (currently only trasnit)
- [ ] Abstract session management (currently only in-memory)
