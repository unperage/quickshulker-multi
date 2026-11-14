# Unper's QuickShulker

[English] | [[简体中文]](docs/README_zh_cn.md)

Open containers straight from your inventory — no placing needed.

### Requirements

- Minecraft **1.20.6 – 26.3**
- Fabric Loader 0.19.3+

### Download

[**Releases**](https://github.com/unperage/unpers-quickshulker/releases) — pick the jar matching your Minecraft version.

### Supported containers

Shulker box (all 17 colours) · Ender chest · Crafting table · Stonecutter · Anvil (all 3 damage levels) · Loom · Smithing table · Grindstone

Each one can be switched off individually in the settings screen.

### Keys

| Action | Default | Change it |
|---|---|---|
| Open the held container | `K` | Options → Controls → Key Binds |
| Open the settings screen | `KP +` | same |
| Nested containers (hold) | `Left Shift` | settings screen |
| Container memory (hold) | `Left Ctrl` | settings screen |

### Mouse

- Right-click a container in the inventory to open it
- Right-click a container with an item to insert it
- Right-click an item with a container to pick it up
- Hold and drag to move stacks in bulk

### Optional (off by default)

- **Nested containers** — open a container that sits inside another container
- **Container memory** — opened containers stack; ESC goes back one level
- **Static pointer** — the cursor stays where it was after a container closes

### Server control

Operators can allow or block each container server-wide:

```
/uqs                        list every container and its state
/uqs <item|all> <true|false>  switch one item, or all of them
```

Tab completion suggests item ids and `all`. The list is saved to `config/quickshulker-server.properties`.

Both sides must agree: a container opens only when the server allows it **and** the player has it enabled locally. A player's own switches never affect anyone else.

### License

MIT. Based on [kyrptonaught/quickshulker](https://github.com/kyrptonaught/quickshulker), continued by [MoRanpcy/quickshulker](https://github.com/MoRanpcy/quickshulker) and [unperage/unpers-quickshulker](https://github.com/unperage/unpers-quickshulker).
