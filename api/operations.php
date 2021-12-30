<?php

//require_once('/home2/stehekin/vendor/firebase/php-jwt/src/JWT.php');
//require_once('/home2/stehekin/vendor/google-api-php-client/google-api-php-client/src/Google/Client.php');
require __DIR__ . '/vendor/autoload.php';
require_once dirname(__FILE__) . '/config.php';
require_once dirname(__FILE__) . '/logger.php';

use Firebase\JWT\JWT;

class Operation
{
    private $con;
    private $auth;
    private const CHALLENGE_TIMEOUT = 1800;
    private $logger;

    function __construct(bool $long_query = false)
    {
        require_once dirname(__FILE__) . '/connect.php';

        $db = new Connect();

        $this->logger = Logger::getInstance();

        $this->con = $db->connect($long_query);
        $this->auth = json_decode(file_get_contents(""), true);
    }


    function pushID(string $token, string $id, array &$response): void
    {
        mysqli_report(MYSQLI_REPORT_ALL ^ MYSQLI_REPORT_INDEX);


        $insert = $this->con->query("INSERT INTO `id_lookup` (`app_id`, `fcm_id`) VALUES (\"$id\", \"$token\") 
        ON DUPLICATE KEY UPDATE `fcm_id`=\"$token\"");


        if (!$insert) {
            $this->build_response($response, false, 'Invalid SQL statement', null, 500);
            return;
        }

        if (!$insert) {
            $this->build_response($response, false, 'An unknown error occurred', null, 500);
            return;
        }
        $this->build_response($response, true, "ID and token inserted/updated successfully", null, 200);
    }

    function getChallenge(string $id, array &$response): void
    {
        $challenge = $this->generateNRandomCharacters();
        $updateChallenge = $this->con->prepare("UPDATE `id_lookup` SET challenge=? WHERE app_id=?");

        if (!$updateChallenge) {
            $this->build_response($response, false, "An error occurred when preparing SQL query for getChallenge", null, 500);
            return;
        }
        $updateChallenge->bind_param('ss', $id, $challenge . " " . (time() + Operation::CHALLENGE_TIMEOUT));

        $updateChallenge->execute();
        
        $this->build_response($response, true, "Challenge provided", $challenge, 200);
    }

    function getToken(string $id, array &$response): void
    {

        $stmt = $this->con->prepare("SELECT fcm_id FROM id_lookup WHERE app_id=?");
        if (!$stmt) {
            $this->build_response($response, false, 'An error occurred while retrieving data', null, 500);
            return;
        }
        $stmt->bind_param('s', $id);

        $stmt->execute();
        $stmt->bind_result($token);
        $stmt->fetch();

        if ($token == '') {
        }

        $success = true;
        $message = 'Retrieved token successfully';
        $data = $token;
        $code = 200;
        $this->build_response($response, $success, $message, $data, $code);
    }

    private function createCustomToken()
    {
        $client_email = $this->auth['client_email'];
        $private_key = $this->auth['private_key'];
        $now_seconds = time();
        $payload = array(
            "iss" => $client_email,
            "sub" => $client_email,
            "aud" => "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit",
            "iat" => $now_seconds,
            "exp" => $now_seconds + (3600),
            "uid" => "attention web app"
        );
        return JWT::encode($payload, $private_key, "RS256");
    }

    private function getOauthToken(): string
    {
        $client = new Google_Client();
        try {
            $client->setAuthConfig(AUTH_CONFIG_PATH);
            $client->addScope(Google_Service_FirebaseCloudMessaging::CLOUD_PLATFORM);

            $savedTokenJson = $this->readFile();

            if ($savedTokenJson != null) {
                $client->setAccessToken($savedTokenJson);

                if ($client->isAccessTokenExpired()) {
                    $accessToken = $this->generateToken($client);
                    $client->setAccessToken($accessToken);
                }
            } else {
                $accessToken = $this->generateToken($client);
                $client->setAccessToken($accessToken);
            }

            $oauthToken = $accessToken['access_token'];
            return $oauthToken;
        } catch (Google_Exception $e) {
            return "An exception occurred";
        }
    }

    function sendAlert(string $to, string $from, string $message, string $signature, array &$response): void
    {
        if (!$this->verifySignature($from, $signature)) {
            $this->build_response($response, false, "Signature verification failed", null, 403);
            return;
        }
        $this->getToken($to, $response);
        if (!$response['success']) {
            $this->build_response($response, false, "Destination token not found", null, 400);
            return;
        }
        $token = $response['data'];
        $url = 'https://fcm.googleapis.com/v1/projects/attention-b923d/messages:send';
        $data = array(
            'message' => array(
                'token' => $token,
                //'name' => strval(time()),
                'data' => array(
                    'alert_to' => $to,
                    'alert_from' => $from,
                    'alert_message' => $message
                ),
                'android' => array(
                    'priority' => 'HIGH'
                )
            )
        );
        $json_data = json_encode($data);
        $FCMToken = $this->getOauthToken();
        $headers = array(
            'Content-Type:application/json',
            "Authorization: Bearer " . $FCMToken
        );

        $ch = curl_init();
        curl_setopt($ch, CURLOPT_URL, $url);
        curl_setopt($ch, CURLOPT_POST, true);
        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, 0);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, false);
        curl_setopt($ch, CURLOPT_POSTFIELDS, $json_data);
        $result = curl_exec($ch);
        if ($result === false) {
            $this->build_response($response, false, "FCM send error " . curl_error($ch), null, 500);
            return;
        }
        if (strpos($result, "error") !== FALSE) {
            $this->build_response($response, false, "An error occurred while sending message", $result, 500);
            return;
        }
        curl_close($ch);
        $this->build_response($response, true, 'Sent message successfully' . $result, $token);
    }

    function build_response(array &$response, bool $success, string $message, ?string $data = null, int $response_code = 200): void
    {
        $response['success'] = $success;
        $response['message'] = $message;
        $response['data'] = $data;
        http_response_code($response_code);
    }

    private function verifySignature(string $id, string $signed): bool {
        // TODO - look up ID and get the challenge from the database (SELECT `challenge` FROM `id_lookup` WHERE `app_id`=id)
        // Use an implementation of ECDSA in PHP to verify the signature and compare it to the challenge - note that ID is the public key
        // Return whether or not they match
        // If they match, delete the challenge from the database (UPDATE `id_lookup` SET challenge="" WHERE `app_id`=id)
        $stmt = $this->con->prepare("SELECT challenge FROM id_lookup WHERE app_id=?");
        if (!$stmt) {
            $this->logger->log("verifySignature: Prepared SQL statement was invalid when attempting to find the challenge for $id");
            return false;
        }
        $stmt->bind_param('s', $id);

        $stmt->execute();
        $stmt->bind_result($token);
        if (!$error_type = $stmt->fetch()) {
            $this->logger->log("verifySignature: Retrieving challenge for id $id failed - fetch() returned $error_type");
            $this->build_response($response, false, 'An error occurred retrieving the challenge for that ID', null, 403);
            return false;
        }
        return openssl_verify();
    }

    private function readFile(): string
    {
        $saved_token = file_get_contents(TOKEN_PATH);
        if ($saved_token === FALSE) return null;
        return $saved_token;
    }

    private function saveFile(string $token)
    {
        file_put_contents(TOKEN_PATH, $token);
    }

    private function generateToken(Google_Client $client)
    {
        $client->fetchAccessTokenWithAssertion();
        $accessToken = $client->getAccessToken();

        $this->saveFile($accessToken);

        return $accessToken;
    }

    /**
     * Generates a string of length n which is guaranteed to be parseable JSON (i.e. no unescaped " or \)
     */
    private function generateNRandomCharacters($n = 16): string {
        $characters = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ`~!@#$%^&*()-_=+[{]}|;:',<.>/?";
        $charactersLength = strlen($characters);
        $randomString = '';
        for ($i = 0; $i < $n; $i++) {
            $randomString .= $characters[rand(0, $charactersLength - 1)];
        }
        return $randomString;
    }
}
