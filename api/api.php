<?php

require_once '../includes/operations.php';

//checks if the params specified in the array are available, returns a standard response array
// 'success' whether all the params were there, 'message' which specifies which params were missing
function paramsAvailable($params) {
    $available = true;
    $missingParams = "";
    $response = array();
    $response['success'] = true;

    foreach ($params as $param) {
        if (!isset($_POST[$param]) || strlen($_POST[$param]) <= 0) {
            $available = false;
            $missingParams = $missingParams . ', ' . $param;
        }
    }

    if (!$available) {
        $response['success'] = false;
        $response['message'] = 'Parameters '. substr($missingParams, 1, strlen($missingParams)) . ' missing';
    }

    return $response;
}


$response = array();
$db = new Operation();
$log_dir = "../includes/log.txt";
$temp_log_dir = "../includes/temp_log.txt";

$log_file = fopen($log_dir, "a");

fwrite($log_file, ((string) date('Y-m-d H:i:s')) .  " IP: {$_SERVER['REMOTE_ADDR']}");

if(isset($_GET['function'])) {

    $function = $_GET['function'];

    $response = array();
    

    $agent = isset($_SERVER['HTTP_USER_AGENT']) ? $_SERVER['HTTP_USER_AGENT'] : null;

    fwrite($log_file, " Agent: $agent called $function");

    //uses the function call to determine what method should be run in the operations.php file
    switch ($function) {
        case 'post_id':
            if (($response = paramsAvailable(array('token', 'id')))['success']) {
                $response = $db->pushID($response, $_POST['token'], $_POST['id']);
            }
        break;
        case 'get_token_for_id':
            if (isset([$_GET['id']])) {
                $response = $db->getToken($user, 'future', $_POST['data'], $response);
            }
        break;
        default:
        $response = $db->build_response($response, false, 'Invalid function specified', null, 400);
    }
} else {
    $response = $db->build_response($response, false, "No function specified", null, 400);
}

fwrite($log_file, " Response: " . json_encode($response), 1024);
fwrite($log_file, "\n");
fclose($log_file);
echo json_encode($response);

if (file_exists($log_dir) && filesize($log_dir) > 5000000) { 
    $shrink_file = fopen($log_dir, 'r');
    $temp_log = fopen($temp_log_dir, 'a');
    fseek($shrink_file, 1000000);
    while ($line = fgets($shrink_file)) {
        fwrite($temp_log, $line);
    }
    fclose($shrink_file);
    fclose($temp_log);
    unlink($log_dir); //delete the large log file
    rename($temp_log_dir, $log_dir); //rename the temp file to be the new log file
}