const fs = require('fs');
function findDuplicates(file) {
    const content = fs.readFileSync(file, 'utf8');
    const lines = content.split('\n');
    const keys = new Map();
    console.log(`Checking ${file}...`);
    lines.forEach((line, i) => {
        const match = line.match(/^\s*'?([^':]+)'?\s*:/);
        if (match) {
            const key = match[1];
            if (keys.has(key)) {
                console.log(`Duplicate: "${key}" at line ${i + 1} (previous at line ${keys.get(key)})`);
            } else {
                keys.set(key, i + 1);
            }
        }
    });
}
findDuplicates('src/i18n/locales/zh.ts');
findDuplicates('src/i18n/locales/en.ts');
