#!/bin/bash
# runs all unit tests excluding Spring/DB ones
cd "$1/Desarrollo/backend"
EX=$(rg -l 'SpringBootTest|DataJpaTest|extends SeguridadIntegracionBase|extends \w*IntegrationBase' src/test --glob '*.java' | xargs -n1 basename | sed 's/.java//' | sed 's/^/!/' | paste -sd,)
./mvnw -q -o test -Dtest="*Test,$EX" -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | rg -v '^\s+at |DEBUG|INFO' | tail -${2:-40}
