import ws from 'k6/ws';
import http from 'k6/http';
import {check, sleep} from 'k6';
import {randomIntBetween} from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const TARGET = __ENV.TARGET || 'local';

const ENV_CONFIG = {
    local: {
        name: 'Local Dev (Single Node)',
        httpBase: 'http://localhost:8080/bidflow',
        wsBase: 'ws://localhost:8080/bidflow/ws'
    },
    cluster: {
        name: 'Docker Cluster (Nginx LB)',
        httpBase: 'http://localhost:80/bidflow',
        wsBase: 'ws://localhost:80/bidflow/ws'
    }
};

const currentEnv = ENV_CONFIG[TARGET];

if (!currentEnv) {
    throw new Error(`Unknown environment: ${TARGET}. Use 'local' or 'cluster'.`);
}

const HTTP_URL = currentEnv.httpBase;
const WS_URL = `${currentEnv.wsBase}/websocket`;

export const options = {
    stages: [
        {duration: '5s', target: 100},   // Warm-up
        {duration: '30s', target: 1000}, // Peak
        {duration: '5s', target: 0},     // Cooldown
    ],
};

export function setup() {
    console.log(`🚀 Starting test in environment: [${currentEnv.name}]`);
    console.log(`📡 HTTP: ${HTTP_URL} | WS: ${WS_URL}`);

    const res = http.post(`${HTTP_URL}/test/reset`);

    if (res.status === 200) {
        console.log('✅ DB reset successfully.');
    } else {
        console.error(`❌ Error on DB reset (${res.status}): ${res.body}`);
    }
}

export default function () {
    const auctionId = 1;
    const username = `user-${randomIntBetween(1, 100000)}`;

    const res = ws.connect(WS_URL, {}, function (socket) {

        socket.on('open', () => {
            const connectFrame = `CONNECT\naccept-version:1.1,1.0\nheart-beat:10000,10000\n\n\u0000`;
            socket.send(connectFrame);
        });

        socket.on('message', (msg) => {
            if (msg.startsWith('CONNECTED')) {
                sleep(randomIntBetween(0.5, 2));

                const amount = randomIntBetween(1000, 90000);

                const bidPayload = JSON.stringify({
                    auctionId: auctionId,
                    bidderUsername: username,
                    amount: amount
                });

                const sendFrame = `SEND\ndestination:/app/bid\ncontent-type:application/json\n\n${bidPayload}\u0000`;
                socket.send(sendFrame);

                sleep(1);
                socket.close();
            }

            if (msg.startsWith('ERROR')) {
                // Optional: uncomment for debug
                // console.error(`STOMP error received: ${msg}`);
            }
        });

        socket.on('close', () => {
        });
    });

    check(res, {'status is 101': (r) => r?.status === 101});
}