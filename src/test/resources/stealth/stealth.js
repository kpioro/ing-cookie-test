(() => {
    const redefine = (target, property, getter) => {
        const descriptor = Object.getOwnPropertyDescriptor(target, property);
        if (!descriptor || descriptor.configurable) {
            Object.defineProperty(target, property, { get: getter, configurable: true, enumerable: true });
        }
    };

    redefine(Navigator.prototype, 'webdriver', () => undefined);

    const languages = Object.freeze(['pl-PL', 'pl', 'en-US', 'en']);
    redefine(Navigator.prototype, 'languages', () => languages);

    if (navigator.userAgent.includes('Chrome/') && !window.chrome) {
        window.chrome = { runtime: {} };
    }

    if (navigator.plugins.length === 0) {
        const plugins = ['PDF Viewer', 'Chrome PDF Viewer', 'Chromium PDF Viewer']
            .map((name) => ({ name, filename: 'internal-pdf-viewer', description: 'Portable Document Format' }));
        const pluginArray = Object.create(PluginArray.prototype, {
            length: { value: plugins.length },
            item: { value: (index) => plugins[index] ?? null },
            namedItem: { value: (name) => plugins.find((plugin) => plugin.name === name) ?? null },
            [Symbol.iterator]: { value: () => plugins[Symbol.iterator]() },
        });
        plugins.forEach((plugin, index) => Object.defineProperty(pluginArray, index, { value: plugin, enumerable: true }));
        redefine(Navigator.prototype, 'plugins', () => pluginArray);
    }

    if (navigator.permissions && typeof Permissions.prototype.query === 'function') {
        const nativeQuery = Permissions.prototype.query;
        Permissions.prototype.query = function query(descriptor) {
            if (descriptor && descriptor.name === 'notifications') {
                const permission = typeof Notification === 'undefined' ? 'default' : Notification.permission;
                return Promise.resolve({ state: permission === 'default' ? 'prompt' : permission, onchange: null });
            }
            return nativeQuery.call(this, descriptor);
        };
    }
})();
