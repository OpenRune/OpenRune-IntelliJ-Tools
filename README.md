# RSCM (RuneScape Config Mapping) - IntelliJ IDEA Plugin

## Setup

Create a directory in your project in which to store your mappings. Mapping files consist of the following format:
```
abyssal_whip=4151
abyssal_whip_note=4152
```
Where `abyssal_whip` is the string representation of the id `4151`.
The name of the mapping file corresponds to the mapping 'type', e.g. `item.rscm` corresponds to `item.abyssal_whip`.

In IntelliJ settings search 'RSCM' and set the mappings directory to the folder you created.

Furthermore, it is possible to do child-references, such as:
```
# in interface.rscm
bank=12

# in component.rscm
bank:universe=0
```

In this example, one is able to refactor `bank` reference in interface.rscm and have it echo through to
the child reference in component.rscm. These child mappings must be defined in RSCM Settings tab, in IntelliJ Idea.
For the above example, the declaration would be `component=interface`.

It is also possible to associate RSCM elements with files in directories. This system requires that
all the files be in a root folder specific to that element. As an example, `data/items/*` where all the files
are `rscm_name.toml`.
In order to set up file references, you need to create a file called `directory.conf` in the same folder
where all the .rscm files are.
The file shall have `type=folder` declarations. For example, `item=items` to link any `item` reference to files in
`items` folder.

Other file extensions beyond .toml are also supported for referential renaming. These require defining the extension
in the `directory.conf` file with the pipe operator.
As an example, a valid declaration is `jingle=jingles|dat`.
This means that when renaming a jingle property (e.g. `jingle.advance_agility`), it will look for a file called
`advance_agility.dat` in the `jingles` folder, and rename it accordingly if one was found. If no suffix is defined,
`toml` files will be searched.

## Features

- Highlighting of mapped strings
- Go to declaration of mapped strings
- Find usages of mapped strings
- Rename mapped strings
  - If using TOML definitions, the plugin will rename files that match the string
- Safe delete mapped strings
- Code completion for mapped strings
- Quick documentation for mapped strings


## Credits
- [ushort](https://github.com/ushort) (Chris)
- [z-kris](https://github.com/z-kris) (Kris)
- [notmeta](https://github.com/notmeta) (Corey)
