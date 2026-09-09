const fs = require('fs');
const path = require('path');

const getAllFafModules = function (cwd = process.cwd()) {
    const packageJsonText = String(fs.readFileSync(`${cwd}/../package.json`, {encoding: 'utf8'}));
    const packageJson = JSON.parse(packageJsonText);
    return packageJson['faf-modules'];
}

const getFafModuleDir = function (fafModule, cwd = process.cwd()) {
    try {
        return path.dirname(require.resolve(`${fafModule}/package.json`, {paths: [cwd]}));
    } catch (e) {
        // Paket dengan field "exports" memblokir subpath yang tidak
        // didaftarkan, dan ./package.json hampir tidak pernah didaftarkan.
        // jquery 4 adalah contohnya: require.resolve('jquery/package.json')
        // gagal dengan ERR_PACKAGE_PATH_NOT_EXPORTED meskipun paketnya ada.
        // Cari langsung di pohon node_modules sebagai cadangan.
        let dir = cwd;
        for (;;) {
            const candidate = path.join(dir, 'node_modules', fafModule);
            if (fs.existsSync(path.join(candidate, 'package.json'))) {
                return candidate;
            }
            const parent = path.dirname(dir);
            if (parent === dir) {
                break;
            }
            dir = parent;
        }
        const message = `Module [${fafModule}] could not be resolved in the following path [${cwd}]`;
        throw new Error(message);
    }
}

module.exports = {
    getAllFafModules,
    getFafModuleDir
}