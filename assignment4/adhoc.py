import os
import logging
import time
import threading
import sys
import socket
import uuid
import select


LOG_FILE = os.getenv('LOG_FILE', 'adhoc_network.log')
PIPE_PATH = os.getenv('PIPE_PATH', '/tmp/adhoc_pipe')
PORT = int(os.getenv('PORT', 5010))
BROADCAST = os.getenv('BROADCAST', '192.168.210.255')
IP = os.getenv('IP')

running = True
seen_messages = set()
sender_socket = None
dsr_payloads = dict()

# Logging configuration
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s [%(levelname)s] %(message)s',
    handlers=[
        logging.FileHandler(LOG_FILE),
        logging.StreamHandler(sys.stdout)
    ]
)


def handle_sigterm(_signue, _frame):
  global running
  logging.info("Received SIGTERM, stopping ad-hoc network...")
  running = False

# These messages can be sent via "./send.sh <protocoll> <ip> <message>"


def handle_message(message):
  logging.info(f"Received send message: {message}")

  if message.startswith("discover"):
    send_discovery_message()
    return

  # split the message into parts
  protocol, rest = message.split(maxsplit=1)
  ip, payload = rest.split(maxsplit=1)

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
    start_dsr_routing(ip, payload)


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
  logging.info(f"Broadcast socket created and bound to {BROADCAST}:{PORT}")

  sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
  sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
  sock.bind((BROADCAST, PORT))
  return sock


def create_uc_socket():
  """Create a UDP socket for unicast messages."""
  logging.info(f"Unicast socket created and bound to {IP}:{PORT}")

  sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
  sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
  sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
  sock.bind((IP, PORT))

  global sender_socket
  sender_socket = sock
  return sock


def send_message(target, msg):
  global sender_socket
  if sender_socket is None:
    logging.error("Sender socket is not initialized. Cannot send message.")
    return

  logging.info(f"Sending \"{msg}\" to ({target}, {PORT})")
  try:
    sender_socket.sendto(msg.encode(), (target, PORT))
  except Exception as e:
    logging.error(
        f"Error sending message to {target}: {e.__class__.__name__}: {e}")


# --------- Flooding -------------

def send_flood_message(target, payload, msg_id=None):
  """FLOOD:MSGID:TARGET:PAYLOAD"""
  if msg_id is None:
    msg_id = str(uuid.uuid4())

  full_msg = f"FLOOD:{msg_id}:{target}:{payload}"
  seen_messages.add(msg_id)
  send_message(BROADCAST, full_msg)

# --------- Discovery -------------


def send_discovery_message():
  """DISCOVER:IP:TIMESTAMP"""
  ts = time.time()
  full_msg = f"DISCOVER:{IP}:{ts}"
  send_message(BROADCAST, full_msg)


def reply_to_discovery_message(sender_ip, timestamp):
  """DISCOVERREP:OTHER:SENDER:TIMESTAMP"""
  full_msg = f"DISCOVERREP:{IP}:{sender_ip}:{timestamp}"
  send_message(sender_ip, full_msg)


# --------- DSR -------------

def start_dsr_routing(target_ip, payload):
  """Initiate DSR routing to the target IP with the given payload."""
  msg_id = str(uuid.uuid4())

  global dsr_payloads
  dsr_payloads[msg_id] = payload

  seen_messages.add(msg_id)
  send_route_request(msg_id, IP, target_ip, [IP])


def send_route_request(msg_id, source, target, nodes):
  """RREQ:MSGID:SOURCE:TARGET:NODE;NODE;..."""
  nodes = ";".join(nodes)
  full_msg = f"RREQ:{msg_id}:{source}:{target}:{nodes}"
  send_message(BROADCAST, full_msg)


def send_route_reply(msg_id, source, target, nodes, receiver):
  """RREP:MSGID:SOURCE:TARGET:NODE;NODE;..."""
  nodes = ";".join(nodes)
  full_msg = f"RREP:{msg_id}:{source}:{target}:{nodes}"
  send_message(receiver, full_msg)


def send_route_data(nodes, payload, receiver):
  """RDATA:NODE;NODE;...:PAYLOAD"""
  nodes = ";".join(nodes)
  full_msg = f"RDATA:{nodes}:{payload}"
  send_message(receiver, full_msg)

# ---------------------------


def listen_for_broadcasts():
  sockets = [create_uc_socket(), create_bc_socket()]

  while running:
    reading, _, _ = select.select(sockets, [], [], 1.0)
    for sock in reading:
      try:
        data, addr = sock.recvfrom(4096)
        if addr[0] == IP:
          logging.info(f"Received message from self ({addr[0]}), ignoring.")
          continue

        msg = data.decode()
        logging.info(f"Received message: {msg} from {addr}")

        if msg.startswith("DISCOVER:"):
          """DISCOVER:IP:TIMESTAMP"""
          _, sender_ip, timestamp = msg.split(":", 2)

          if sender_ip != IP:
            reply_to_discovery_message(sender_ip, timestamp)

        elif msg.startswith("DISCOVERREP:"):
          """DISCOVERREP:OTHER:SENDER:TIMESTAMP"""
          _, other_ip, sender_ip, timestamp = msg.split(":", 3)

          assert sender_ip == IP, "Sender IP in DISCOVERREP does not match local IP"
          rtt = (time.time() - float(timestamp)) * \
              1000  # Convert to milliseconds
          latency = rtt / 2  # Round-trip time divided by 2 for one-way latency
          logging.info(f"Latency {addr} <-> {other_ip} = {latency:.2f} ms")

        elif msg.startswith("FLOOD:"):
          _, msg_id, target, payload = msg.split(":", 3)
          if msg_id not in seen_messages:
            seen_messages.add(msg_id)

            # Re-broadcast
            if target != IP:
              send_flood_message(target, payload, msg_id)
            else:
              logging.info(f"Received flood message for me: {payload}")

        elif msg.startswith("RREQ:"):
          """RREQ:MSGID:SOURCE:TARGET:NODE;NODE;..."""
          _, msg_id, source, target, nodes = msg.split(":", 4)

          if msg_id not in seen_messages:
            seen_messages.add(msg_id)

            nodes = nodes.split(";")
            nodes.append(IP)

            if nodes[-1] != target:
              send_route_request(msg_id, source, target, nodes)
            else:
              logging.info(
                  f"Received complete route request for {source} ---> {target} with nodes: {nodes}")
              # Send reply to the previous node
              send_route_reply(msg_id, source, target, nodes, nodes[-2])

        elif msg.startswith("RREP:"):
          """RREP:MSGID:SOURCE:TARGET:NODE;NODE;..."""
          _, msg_id, source, target, nodes = msg.split(":", 4)
          nodes = nodes.split(";")

          idx = nodes.index(IP)
          if idx == 0:
            logging.info(
                f"Received complete route reply for {source} ---> {target} with nodes: {nodes}")
            assert nodes.pop(
                0) == IP, "First node in RREP must be the local IP"
            receiver = nodes.pop(0)
            payload = dsr_payloads.pop(msg_id, None)
            if payload is None:
              logging.error(f"No payload found for msg_id {msg_id}")
              continue

            send_route_data(nodes, payload, receiver)
          else:
            # Forward the route reply to the next node
            next_node = nodes[idx - 1]
            send_route_reply(msg_id, source, target, nodes, next_node)

        elif msg.startswith("RDATA:"):
          """RDATA:NODE;NODE;...:PAYLOAD"""
          _, nodes, payload = msg.split(":", 2)
          nodes = nodes.split(";")

          if len(nodes) > 0 and nodes[0] != '':
            receiver = nodes.pop(0)
            send_route_data(nodes, payload, receiver)
          else:E
            logging.info(f"Received route data for me: {payload}")

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
