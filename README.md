# 🌉 MCBridge - Minimalist Minecraft Relay & LAN Mod

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-blue.svg?style=flat-square)](https://www.minecraft.net/)
[![Forge Version](https://img.shields.io/badge/Forge-47.2.0-orange.svg?style=flat-square)](https://files.minecraftforge.net/)

[中文文档 (Chinese)](README_CN.md)

**MCBridge** is an automated intranet penetration tool designed specifically for Minecraft. It integrates high-performance `frp` technology, allowing you to share your local world to the public internet with one click, completely saying goodbye to tedious port mapping and network configuration.

---

## 🚀 Key Features

- **⚡ One-Click Mapping**: A simple button in the "Open to LAN" screen publishes your game world to the internet instantly.
- **🧠 Auto-Optimization**: Automatically pings all built-in nodes and selects the fastest server for your connection.
- **✅ Multi-Instance Support**: Perfect support for running multiple Minecraft clients simultaneously, creating independent mappings for each without interference.
- **🛡️ Secure by Design**: Credentials passed via environment variables, temporary config files deleted immediately, and all remote communication is TLS-encrypted.
- **🔄 Failover Support**: Built-in primary and backup addresses; seamlessly switches to backup IP if the primary domain fails.
- **🌐 Dynamic Ports**: Each mapping receives a fresh random port from the server to avoid conflicts.
- **🧩 Smart Compatibility**: Fully compatible with the vanilla "Open to LAN" screen and enhancement mods like `mcwifipnp` (LAN World Plug-n-Play).
- **💾 State Persistence**: Remembers your last mapping preference, no need to re-configure next time.

---

## 🛠️ User Guide

The logic of this mod is simple: **Only the host needs to operate; friends joining the game don't need any extra steps.**

### As the Host (Server Side)

1.  Enter your single-player world.
2.  Press `Esc` and click **"Open to LAN"**.
3.  Find the **"Use Relay Mapping"** option and toggle it to **"ON"**.
4.  Click **"Start LAN World"**.
5.  Wait a moment; the mod will automatically test speed and connect. A success message will appear in chat with two public addresses:
    > **[MCBridge] Mapping successful! (Node: Guangdong Shenzhen [New] - 🚀 Shenzhen Telecom)**  
    > **Domain Address: nat.mrcao.com.cn:30938**  
6.  Copy **either** of these addresses (domain address recommended) and send it to your friends.

### As a Friend (Client Side)

1.  Go to **"Multiplayer"** from the main menu.
2.  Click **"Add Server"** or **"Direct Connection"**.
3.  In the "Server Address" field, **paste the full address** provided by the host (e.g., `nat.mrcao.com.cn:30938`).
4.  Join the server and start playing!

**Note**: Client players **do not** need to install this mod or perform any "relay" operations. They only need the correct address.

---

## ⚙️ Built-in Node Info

The following high-performance nodes are integrated by default, maintained by the community:

- **Node Name**: Guangdong Shenzhen [New] - 🚀 Shenzhen Telecom
- **Primary Server**: `nat.mrcao.com.cn`
- **Security**: Mandatory TLS encrypted connection

Want to contribute more nodes? Check [NODES.md](NODES.md) or [Fill out the online form](https://f.wps.cn/g/LlHLlCNh/).

---

## 🤝 How to Contribute

We welcome contributions from the community! Here’s how you can get involved:

- **Provide Nodes**: If you have a stable frp server and are willing to share it with the community, please submit it via [NODES.md](NODES.md) or our [WPS Form](https://f.wps.cn/g/LlHLlCNh/).
- **Code Contributions**: Found a bug or have a feature request? Feel free to open an Issue or submit a Pull Request.
- **Localization**: Help us translate the mod and documentation into more languages.
- **Feedback**: Share your experience and suggestions in our community channels.

---

## 📦 Installation

1.  Ensure **Minecraft Forge 1.20.1** (or compatible version) is installed.
2.  Place the `.jar` file into your game's `mods` folder.
3.  Launch the game!

---

## 📄 Legal Disclaimer

This mod is for educational and technical exchange purposes only. Do not use it for any commercial or illegal purposes. Users assume all risks associated with using public relay nodes.

---

## ☕ Support the Developer

If you find this mod helpful, consider supporting the developer's continuous efforts!

<div align="center">
  <img src="WeChat.jpg" width="200" alt="Wechat Pay" />
  <img src="Alipay.jpg" width="200" alt="Alipay" />
  <p>Wechat Pay & Alipay</p>
</div>

---

**✨ Have fun playing!**
