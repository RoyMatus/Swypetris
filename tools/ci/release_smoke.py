"""Exercise the actual signed release through accessibility, without a test APK."""

import argparse
from contextlib import contextmanager
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


@contextmanager
def smoke_diagnostics(adb, output, observations):
    """Attempt every diagnostic without replacing a failure from the exercised UI."""
    failed = False
    try:
        yield
    except BaseException:
        failed = True
        raise
    finally:
        errors = []
        log = ''
        try:
            (output / 'observations.json').write_text(
                json.dumps(observations, ensure_ascii=False, indent=2), encoding='utf-8')
        except OSError as error:
            errors.append(error)
        try:
            log = adb('logcat', '-d', '-v', 'threadtime')
            (output / 'logcat.txt').write_text(log, encoding='utf-8')
        except (OSError, subprocess.SubprocessError) as error:
            errors.append(error)
        try:
            adb('shell', 'rm', '/sdcard/swypetris-release-smoke.xml')
        except (OSError, subprocess.SubprocessError) as error:
            errors.append(error)
        for error in errors:
            print(f'Release smoke diagnostics failed: {error}', file=sys.stderr)
        if not failed:
            if errors:
                raise RuntimeError('Release smoke diagnostics are incomplete') from errors[0]
            if 'FATAL EXCEPTION' in log and 'Process: ru.itoltec.swypetris' in log:
                raise RuntimeError('Signed release produced an Android runtime crash')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    observations = []

    def adb(*command, binary=False):
        completed = subprocess.run(['adb', '-s', args.serial, *command],
                                   capture_output=True, timeout=40, check=True)
        return completed.stdout if binary else completed.stdout.decode('utf-8', errors='replace')

    def nodes():
        adb('shell', 'uiautomator', 'dump', '--compressed', '/sdcard/swypetris-release-smoke.xml')
        xml = adb('shell', 'cat', '/sdcard/swypetris-release-smoke.xml')
        return list(ET.fromstring(xml).iter('node'))

    def find(label, prefix=False):
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            for node in nodes():
                values = (node.get('text', ''), node.get('content-desc', ''))
                matches = any(value.startswith(label) if prefix else value == label for value in values)
                if matches and node.get('enabled') == 'true':
                    return node
            time.sleep(.5)
        raise RuntimeError(f'Release UI did not expose {label!r}')

    def click(label):
        node = find(label)
        bounds = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
        if not bounds:
            raise RuntimeError('Release UI node has invalid bounds')
        left, top, right, bottom = map(int, bounds.groups())
        adb('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))
        observations.append(f'clicked {label}')

    def screenshot(name):
        (args.output / f'{name}.png').write_bytes(adb('exec-out', 'screencap', '-p', binary=True))

    adb('logcat', '-c')
    installation = adb('install', '-r', str(args.apk))
    if 'Success' not in installation:
        raise RuntimeError('Signed release APK installation did not succeed')
    with smoke_diagnostics(adb, args.output, observations):
        adb('shell', 'am', 'start', '-W', '-n', 'ru.itoltec.swypetris/.MainActivity')
        find('Новая игра')
        screenshot('release-menu')
        click('Настройки')
        find('ИГРОВОЙ ПРОЦЕСС')
        screenshot('release-settings')
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        find('Новая игра')
        click('Новая игра')
        find('Очки ', prefix=True)
        screenshot('release-game')
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        find('Продолжить')
        click('Продолжить')
        find('Очки ', prefix=True)
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        find('Продолжить')
        observations.append('signed release launch, settings, game, pause/continue passed')
    print('Actual signed release smoke passed: menu, settings, new game, pause and Continue.')


if __name__ == '__main__':
    main()
