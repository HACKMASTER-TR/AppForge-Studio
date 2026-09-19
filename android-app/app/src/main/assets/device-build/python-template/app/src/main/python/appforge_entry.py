import importlib
import traceback


def run():
    try:
        module = importlib.import_module("main")

        entry = getattr(
            module,
            "main",
            None,
        )

        if callable(entry):
            result = entry()
            return "" if result is None else str(result)

        value = getattr(
            module,
            "APPFORGE_OUTPUT",
            None,
        )

        if value is not None:
            return str(value)

        return (
            "Python projesi yüklendi.\n"
            "main.py içinde main() fonksiyonu tanımla."
        )

    except Exception:
        return traceback.format_exc()
