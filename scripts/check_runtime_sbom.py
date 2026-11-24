#!/usr/bin/env python3
"""Pure Python tests for the SBOM mapping, including fail-closed inputs."""
import unittest
from generate_runtime_sbom import generate, CONFIGS

class RuntimeSbomTest(unittest.TestCase):
    def setUp(self):
        self.inventory = {config: [{"group": "androidx.test", "name": "sample", "version": "1.2.3"}]
                          for config in CONFIGS}
        self.cargo = {"package": [{"name": "legion-core", "version": "0.1.0"}]}

    def bom(self, config=CONFIGS[0]):
        return generate(self.inventory, self.cargo, "0.1.0-alpha.48", config, "a"*40)

    def test_runtime_components_and_local_native_identity(self):
        bom = self.bom()
        self.assertEqual("CycloneDX", bom["bomFormat"])
        self.assertEqual("1.5", bom["specVersion"])
        self.assertEqual("0.1.0-alpha.48", bom["metadata"]["component"]["version"])
        self.assertEqual({"pkg:maven/androidx.test/sample@1.2.3", "local:legion-core:0.1.0"},
                         {p["bom-ref"] for p in bom["components"]})
        self.assertFalse(any(p.get("purl", "").startswith("pkg:cargo/legion-core")
                             for p in bom["components"]))

    def test_variants_are_separate_and_duplicate_coordinates_deduplicated(self):
        self.inventory[CONFIGS[0]] *= 2
        self.inventory[CONFIGS[1]][0]["version"] = "2.0.0"
        self.assertEqual(2, len(self.bom()["components"]))
        self.assertIn("pkg:maven/androidx.test/sample@2.0.0",
                      {p["bom-ref"] for p in self.bom(CONFIGS[1])["components"]})

    def test_reject_empty_and_unhandled_git_sources(self):
        self.inventory[CONFIGS[0]] = []
        with self.assertRaises(ValueError):
            self.bom()
        self.inventory[CONFIGS[0]] = self.inventory[CONFIGS[1]]
        self.cargo["package"].append({"name":"git-only","version":"0.1.0","source":"git+https://example.org/repo"})
        with self.assertRaises(ValueError):
            self.bom()

if __name__ == "__main__":
    unittest.main()
