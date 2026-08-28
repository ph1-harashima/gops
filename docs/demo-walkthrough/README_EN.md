# G-SYS Online Ordering Prototype Walkthrough

This walkthrough is not the 9/17 customer demo script - it's a reference for understanding what the system does and how it works, screen by screen.

## Business Flow

```
Order Candidates
  ↓
Draft
  ↓
PO Preview
  ↓
Order Confirmed
  ↓
Demo Send
  ↓
Supplier Response
  ↓
History
```

**A Portal that takes the stock, sales, and open-PO data already in G-SYS, proposes a Recommended Qty, lets a person decide the actual order quantity, and tracks the process all the way through to the supplier's response.**

---

## 1. Dashboard

![Dashboard](01-dashboard.png)

### What this screen is

The first screen after login. It's the entry point for seeing, at a glance, how many things need attention right now. This is not a Sales Trend or margin-analysis Analytics screen - it's today's action starting point (an Action / Operation Cockpit).

### What to look at

- Order Candidates: number of SKUs with a Recommended Qty greater than zero
- Out of Stock / Long-term Out of Stock: number of SKUs currently out of stock
- Drafts in Progress: Drafts that have been created but not yet confirmed
- Awaiting Supplier Response: orders sent to the supplier and waiting on an answer
- Needs Attention: number of Attentions still waiting to be acknowledged
- Brand breakdown table: the same counts broken down by Brand

### What happens next

Click the "Order Candidates" tile or a Brand row to go to the Order Candidate List.

---

## 2. Order Candidate List

![Order Candidate List](02-order-candidates.png)

### What this screen is

Uses the stock, sales history, and open-PO data from Legacy G-SYS together with the existing ordering Formula to show, in one list, "what should we order, and how much."

### What to look at

- Item name, Brand, Supplier
- Current Stock, Safety Stock, Open PO
- This month's sales, Lead Time
- Recommended Qty: notice that both SKUs with a Recommended Qty of 0 and SKUs with a positive Recommended Qty appear together in the same list

### What happens next

Select the SKUs you want to order using the checkboxes, then click "Create Draft" to move to the Order Draft screen.

---

## 3. Order Draft

![Order Draft](03-order-draft.png)

### What this screen is

The system doesn't decide the final quantity - a person does, using the Recommended Qty as a reference point. This screen is where that human judgment call actually happens.

### What to look at

- Recommended Qty sitting next to Order Qty
- A "Needs Attention" warning that appears when the entered quantity deviates significantly from the recommendation
- Unit price and line amount per SKU, plus total quantity and total amount
- "Save Draft" can be used to save and re-edit as many times as needed

### What happens next

Once the content is ready, click "PO Preview" to move to the PO Preview screen.

---

## 4. PO Preview

![PO Preview](04-po-preview.png)

### What this screen is

The final check before the order actually goes to the supplier. It's a read-only display of the Draft's content - quantities cannot be edited directly from this screen.

### What to look at

- Order quantity, unit price, and amount per SKU
- Total order quantity and total order amount
- A preview of what would be sent to the supplier (recipient, subject, body)
- The note confirming "this is Demo Mode - no email is actually sent"

### What happens next

"Confirm Order" assigns the PO number, and the following "Send to Supplier" (Demo Send) step moves you to the Supplier Response screen.

---

## 5. Supplier Response

![Supplier Response](05-supplier-response.png)

### What this screen is

Placing the order isn't the end of the process - this screen records what the supplier actually confirms they can deliver (quantity and date), and tracks any difference from what was ordered.

### What to look at

- Ordered quantity sitting next to confirmed quantity, per SKU
- SKUs where the confirmed quantity matches the ordered quantity (no difference) versus SKUs where it differs (flagged with a "Quantity Changed" Attention)
- Response date and response notes
- "Save Response" can be used any number of times; "Confirm Response" becomes available once every SKU has an answer

### What happens next

After "Confirm Response," click "View History" to go to the Order History screen.

---

## 6. Order History / Timeline

![Order History](06-order-history.png)

### What this screen is

Lets you trace, after the fact, both the quantity's journey - "Recommended → Human decision → Supplier's answer" - and exactly who did what, and when.

### What to look at

- The three-stage comparison per SKU: Recommended Qty, Ordered Qty, Confirmed Qty
- The Attention flags shown on any SKU with a difference
- The "Activity History" section at the bottom: every step from Draft creation through Order Confirmation, Demo Send, and Supplier Response confirmation, each recorded with who performed it and when

### What happens next

This completes one full pass through the Core Workflow. Going back to the Dashboard, you'll see the "Needs Attention" count and related figures now reflect this order's state.
