# Unper's QuickShulker

[English] | [[简体中文]](docs/README_zh_cn.md)

Open containers straight from your inventory

### Requirements

- Minecraft **1.20.6 – 26.3**
- Fabric Loader 0.19.3+

### Download

[**Releases**](https://github.com/unperage/unpers-quickshulker/releases) — pick the jar matching your Minecraft version.

### Supported containers (from quickshulker-multi)

Shulker box · Ender chest · Crafting table · Stonecutter · Anvil · Loom · Smithing table · Grindstone

Each one can be switched off individually

### Keys

| Action | Default | Change it |
|---|---|---|
| Open the held container | `K` | Options → Controls → Key Binds |
| Open the settings screen | `KP +` | same |
| Nested containers (hold) | `Left Shift` | settings |
| Container memory (hold) | `Left Ctrl` | settings |

### Mouse

- Right-click a container in the inventory to open it
- Right-click a container with an item to insert it
- Right-click an empty slot with a container to take an item out
- Hold and drag to move items in bulk

### Optional (off by default)

- **Nested containers** — containers outside the inventory can be opened
- **Container memory** — remembers the layers of nested containers you opened
- **Static pointer** — puts the cursor back where it was

### Server control

Operators can allow or block each container:

```
/uqs                        list every container and its state
/uqs <item|all> <true|false>  switch one item, or all of them
```

Tab completion suggests item ids and `all`. The list is saved to `config/quickshulker-server.properties`.

Both sides must allow it: the server **and** the player locally. Players do not affect each other.

### License

MIT. Based on [kyrptonaught/quickshulker](https://github.com/kyrptonaught/quickshulker), continued by [MoRanpcy/quickshulker](https://github.com/MoRanpcy/quickshulker) and [unperage/unpers-quickshulker](https://github.com/unperage/unpers-quickshulker).
