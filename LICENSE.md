# License

Sketchware Neo is **source-available**, not open source in the formal definition.

The source code is published so the community can view it, contribute to it, and build it locally. However, because the underlying codebase derives from the original Sketchware — whose copyright belongs to its original developers — neither the contributors to this repository nor the maintainers of Sketchware Neo hold a clear, independent copyright over the codebase as a whole.

### What this means in practice

- You **may** view, fork, and build this project for personal use.
- You **may** submit contributions (bug fixes, features, improvements) via pull requests.
- You **may not** redistribute Sketchware Neo — original or modified — on the Play Store or any other public app marketplace.
- You **should not** use substantial portions of this codebase in unrelated commercial or public projects, as doing so would likely infringe on the original Sketchware copyright.

When in doubt, ask before reusing code from this repository in another project.

---

## Exceptions

Since Kotlin compilation support was added to Sketchware Neo projects, two additional modules were introduced to the codebase:

- `build-logic`
- `kotlinc`

Both were taken from [CodeAssist](https://github.com/tyron12233/CodeAssist), which is licensed under **GPL-3.0**. These two modules are therefore licensed under GPL-3.0 as required by that license. See each module's directory for details.

All other parts of this repository remain under the source-available terms described above.
