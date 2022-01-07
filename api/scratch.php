<?php
function generateNRandomCharacters($n = 16): string {
        $characters = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`~!@#$%^&*()-_=+[{]}|;:',<.>/?";
        $charactersLength = strlen($characters);
        $randomString = '';
        for ($i = 0; $i < $n; $i++) {
            $randomString .= $characters[rand(0, $charactersLength - 1)];
        }
        return $randomString;
    }
    
echo generateNRandomCharacters();

echo "\n";

echo "Result of verification:";
$output = openssl_verify("c~2:sdnzROtT[-Wi", base64_decode("MEUCIQDiE5nCHOf5z9KEoB5P94JPh2YLih8tUddFtHGxzwMCzAIgXxG5jQ/He2iM4WoHM9fzYdLH8R5eMkhdK2rpPZDsAcc="), base64_decode("MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEWa9anYkcwnY+R5b4+ri7xiQKxMswxCV4VrYlXfVnCSQv4OW4E2gcDOzAjzYi48iJj90uEdMOhqupsBog2irhUA=="), OPENSSL_ALGO_SHA512);

if ($output == 1) {
echo "SUCCESS";
}
else if ($output == 0) {
echo "MISMATCH";
} else {
echo "ERROR";
}
