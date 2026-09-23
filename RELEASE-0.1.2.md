PlayerSettings 0.1.2 replaces the horizontally sliced settings UI with whole bitmap panels and buttons using the supplied HallowPrison skin. Font advances are measured from actual pixels, and a dedicated menu font prevents row alignment from depending on the player's default font.

The six settings pages, search, personal toggles, dropdown, and existing command integration remain available. Button labels and icons are aligned, dropdown options no longer overlap the trigger, and independent icon actions receive explicit hit regions.

Validation: four JUnit tests; native TabooLib build checks; independent bitmap metrics validation; actual Minecraft 26.2 line splitting (29 rows, 450 pixels each); test-server plugin startup. The final visual appearance still needs an in-game check with the newly delivered pack.

Deploy the matching JAR and resource pack together. Rebuild and upload with `/ce workflow default`, then verify the hosted ZIP contains the new font providers. This deployment found that the previously hosted ZIP still contained an older settings font.

On this machine, CraftEngine's workflow hit an existing cache-concurrency exception. The settings namespace was installed into both existing ZIPs directly, with byte-for-byte verification that unrelated entries were preserved; the server then stopped cleanly. The upstream workflow issue remains unresolved. GitHub source synchronization, tag and Release are pending because this machine has no usable GitHub credentials.
