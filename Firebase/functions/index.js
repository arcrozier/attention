const functions = require('firebase-functions');

const admin = require("firebase-admin");
var request = require('request');
admin.initializeApp();

ref = admin.database.ref();

// Assuming userIds are just being added somewhere else, somehow

exports.sendAlert = functions.https.onCall(async (req, res) => {
    const message = req.query.message;
    const toId = req.query.to;
    const fromId = req.query.from;
    const getUserTokenPromise = admin.database().ref(`/users/${toId}/token`).once('value');

    let tokensSnapshot;
    let tokens;

    const results = await Promise.all([getUserTokenPromise]);
    tokensSnapshot = results[0];

    if (!tokensSnapshot.hasChildren()) {
        return console.log('No notification toeksn found');
    }

    console.log('There are', tokensSnapshot.numChildren(), 'tokens to send notifications to');

    tokens = Object.keys(tokensSnapshot.val());

    const payload = {
        data: {
            to: toId,
            from: fromId,
            message: message
        }
    }

    const response = await admin.messaging().sendToDevice(tokens, payload);

    response.results.forEach((result, index) => {
        const error = result.error;
        if (error) {
            console.error('Failure sending notification to', tokens[index], error);
        } else {
            console.log('Sent message to', tokens[index]);
        }
    })
})


// // Create and Deploy Your First Cloud Functions
// // https://firebase.google.com/docs/functions/write-firebase-functions
//
// exports.helloWorld = functions.https.onRequest((request, response) => {
//   functions.logger.info("Hello logs!", {structuredData: true});
//   response.send("Hello from Firebase!");
// });
