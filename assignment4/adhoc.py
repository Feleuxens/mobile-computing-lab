import os
import logging
import time
import threading
import sys
import socket
import uuid
import select
# from dotenv import load_dotenv
#
# load_dotenv()

LOG_FILE = os.getenv('LOG_FILE', 'adhoc_network.log')
PIPE_PATH = os.getenv('PIPE_PATH', '/tmp/adhoc_pipe')
PORT = int(os.getenv('PORT', 5010))
BROADCAST = os.getenv('BROADCAST', '192.168.210.255')
IP = os.getenv('IP')

running = True
seen_messages = set()

sender_socket = None

# Logging configuration
logging.basicConfig(
    level=logging.INFO,  # Set to DEBUG for more verbosity
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.FileHandler(LOG_FILE),
        logging.StreamHandler(sys.stdout)  # Optional: log to console too
    ]
)

def handle_sigterm(signue, frame):
    global running
    logging.info("Received SIGTERM, stopping ad-hoc network...")
    running = False

### These messages can be sent via "./send.sh <protocoll> <ip> <message>"
def handle_message(message):
    logging.info(f"Received send message: {message}")

    if message.startswith("discover"):
        send_discovery_message()
        return

    # split the message into parts
    protocol, rest = message.split(maxsplit=1)
    ip, payload = rest.split(maxsplit=1)

    # TODO: Validate protocol and IP
    logging.info(f"Protocol: {protocol}, IP: {ip}, Payload: {payload}")

    if protocol == "flood":
        if not ip:
            logging.error("No IP address provided for flooding.")
            return
        if not payload:
            logging.error("No payload provided for flooding.")
            return

        logging.info(f"Flooding message: {payload} to {ip}")
        send_flood_message(ip, payload)

    elif message.startswith("dsr "):
        if not ip:
            logging.error("No IP address provided for dsr.")
            return
        if not payload:
            logging.error("No payload provided for dsr.")
            return

        logging.info(f"Sending DSR message: {payload} to {ip}")
        # TODO: implement DSR routing logic


def listen_for_messages():
    if os.path.exists(PIPE_PATH):
        os.remove(PIPE_PATH)
    os.mkfifo(PIPE_PATH)

    logging.info(f"FIFO pipe created at {PIPE_PATH}")
    while running:
        with open(PIPE_PATH, 'r') as fifo:
            for line in fifo:
                if line:
                    handle_message(line.strip())

    if os.path.exists(PIPE_PATH):
        os.remove(PIPE_PATH)



# -------------------------
def create_bc_socket():
    """Create a UDP socket for broadcast messages."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.bind((BROADCAST, PORT))
    return sock

def create_uc_socket():
    """Create a UDP socket for unicast messages."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((IP, PORT))
    return sock

def send_message(target, msg):
    # if target != BROADCAST:
    #     logging.info(f"Sending \"{msg}\" via uc to ({target}, {PORT})")
    #     s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    #     s.sendto(msg.encode(), (target, PORT))
    #     s.close()
    #     return

    global sender_socket
    if sender_socket is None:
        sender_socket = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sender_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT, 1)
        sender_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sender_socket.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        sender_socket.bind((IP, PORT))

    logging.info(f"Sending \"{msg}\" to ({target}, {PORT})")
    try:
        sender_socket.sendto(msg.encode(), (target, PORT))
    except Exception as e:
        logging.error(f"Error sending message to {target}: {e.__class__.__name__}: {e}")




### FLOOD:MSGID:TARGET:PAYLOAD
def send_flood_message(target, payload, msg_id=None):
    if msg_id is None:
        msg_id = str(uuid.uuid4())

    full_msg = f"FLOOD:{msg_id}:{target}:{payload}"
    seen_messages.add(msg_id)
    send_message(BROADCAST, full_msg)


### DISCOVER:IP:TIMESTAMP
def send_discovery_message():
    ts = time.time()
    full_msg = f"DISCOVER:{IP}:{ts}"
    send_message(BROADCAST, full_msg)


### DISCOVERREP:OTHER:SENDER:TIMESTAMP
def reply_to_discovery_message(sender_ip, timestamp):
    full_msg = f"DISCOVERREP:{IP}:{sender_ip}:{timestamp}"
    send_message(sender_ip, full_msg)


def listen_for_broadcasts():
    sockets = [create_uc_socket(), create_bc_socket()]

    while running:
        reading, _, _ = select.select(sockets, [], [])  # Timeout to allow graceful shutdown

        logging.info(f"Listening for broadcasts on {len(reading)} sockets...")

        for sock in reading:
            try:
                data, addr = sock.recvfrom(4096)
                msg = data.decode()
                logging.info(f"Received message: {msg} from {addr}")

                if msg.startswith("DISCOVER:"):
                    # DISCOVER:IP:TIMESTAMP
                    _, sender_ip, timestamp = msg.split(":", 2)

                    if sender_ip != IP:
                        reply_to_discovery_message(sender_ip, timestamp)

                elif msg.startswith("DISCOVERREP:"):
                    # DISCOVERREP:OTHER:SENDER:TIMESTAMP
                    _, other_ip, sender_ip, timestamp = msg.split(":", 3)

                    assert sender_ip == IP, "Sender IP in DISCOVERREP does not match local IP"
                    logging.info(f"Discovery reply received from {addr}. Other IP: {other_ip}, Latency: {(time.time() - float(timestamp)) / 1000} ms")

                elif msg.startswith("FLOOD:"):
                    _, msg_id, target, payload = msg.split(":", 3)
                    if msg_id not in seen_messages:
                        seen_messages.add(msg_id)

                        # Re-broadcast
                        if target != IP:
                            send_flood_message(target, payload, msg_id)

            except Exception as e:
                logging.error(f"Broadcast listener error: {e.__class__.__name__}: {e}")


def main():
    logging.info("Ad-hoc network starting...")

    # Messages from ./send_adhoc.sh will be handled here
    threading.Thread(target=listen_for_messages, daemon=True).start()

    listen_for_broadcasts()

    logging.info("Ad-hoc network stopped.")


if __name__ == "__main__":
    main()
