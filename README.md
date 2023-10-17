
Original source : 
https://github.com/WooMinecraft/woominecraft-wp
https://github.com/WooMinecraft/WooMinecraft

# This is an edited version of the original plugin which adds support to offline minecraft servers with additional qualitify of life features.

# WooMinecraft - Self-hosted Minecraft Donations

[WooMinecraft](http://woominecraft.com) is a free Minecraft Donation plugin for your server that provides a self-hosted solution where you're the boss.  By leveraging a well known eCommerce plugin on the
WordPress side of things, we allow you to specify commands for product variations, general product commands, and resending of donations at any time.   
![WooMinecraft Logo](https://raw.githubusercontent.com/WooMinecraft/WooMinecraft/main/src/main/resources/wmc-logo.jpg)

## Config
Your config should look like the below section.
```
# Set this to the desired language file you wish to load.
#
# If your l10n is not available but one is that you know how to speak,consider
# contributing to this plugin at https://github.com/WooMinecraft/WooMinecraft/
lang: "en"
# This is how often, in seconds, the server will contact your WordPress installation
# to see if there are donations that need made.
update_interval: 1500
# You must set this to your WordPress site URL.  If you installed WordPress in a
# subdirectory, it should point there.
url: "http://playground.dev"
# If you are having issues with REST, or have disabled pretty permalinks. Set this to false.
# Doing so will use the old /index.php?rest_route=/wmc/v1/server/ base
# Setting this to false will also allow you to set the restBasePath value if you have altered your
# installation in any way.
prettyPermalinks: true
# If your REST API has a custom path base, input it here. 
# NOTE: This is only loaded if prettyPermalinks is set to false.
# Known good URL bases.
# - /wp-json/wmc/v1/server/
# - /index.php?rest_route=/wmc/v1/server/
restBasePath: ""
# This must match the WordPress key in your admin panel for WooMinecraft
# This is a key that YOU set, both needing to be identical in the admin panel
# and in this config file
# For security purposes, you MUST NOT leave this empty.
key: ""
# Allowed worlds the player needs to be in to run the commands.
# Disabled by default!
#whitelist-worlds:
#  - world
# Set to true in order to toggle debug information
debug: false
```


### WordPress Plugin
You'll need the WordPress plugin for this MC Plugin to work - you can [get it here](https://github.com/WooMinecraft/woominecraft-wp).

## Changelog

## 1.4.6
* Added support for offline minecraft servers
* Added logging of order commands being used ingame saved in log.yml with player name and order id inside the WooMinecraft folder
* Added command /woo clearlog which clears the logfile saved in the WooMinecraft folder
