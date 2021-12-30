<?php

require_once 'operations.php';
require_once dirname(__FILE__) . '/config.php';

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

$logger = Logger::getInstance();

if(isset($_GET['function'])) {

    $function = $_GET['function'];

    $response = array();
    

    $agent = isset($_SERVER['HTTP_USER_AGENT']) ? $_SERVER['HTTP_USER_AGENT'] : null;

    fwrite($log_file, " Agent: $agent called $function");

    //uses the function call to determine what method should be run in the operations.php file
    switch ($function) {
        case 'post_id':
            if (($response = paramsAvailable(array('token', 'id')))['success']) {
                $db->pushID($_POST['token'], $_POST['id'], $response);
            }
        break;
        case 'send_alert':
            if (($response = paramsAvailable(array('to', 'from', 'message', 'signature')))['success']) {
                $db->sendAlert($_POST['to'], $_POST['from'], $_POST['message'], $_POST['signature'], $response);
            } 
        break;
        case 'get_challenge':
            // TODO - verify that id is set, then call $db->getChallenge($id, $response)
        break;
        default:
        $db->build_response($response, false, 'Invalid function specified', null, 400);
    }
} else {
    $db->build_response($response, false, "No function specified", null, 400);
}

$logger->log(" Response: " . json_encode($response), true, 1024);
echo json_encode($response);

