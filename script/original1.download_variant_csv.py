
import logging
import os
import csv
from selenium import webdriver
from selenium.webdriver.common.by import By
import tqdm
wd = webdriver.Chrome()
wd.implicitly_wait(3)     # wait up to this many seconds for pages to load
logging.basicConfig(format='%(asctime)-15s: %(message)s', level=logging.INFO)
logger = logging.getLogger()

def log(message):
    logger.info("---> " + message)

def url_get(wd, url):
    log("Open " + url)
    wd.get(url)
    log("     Opened")

def find_element(wd, by, value):
    log("Finding elem by %s: %s" % (by, value))
    elem = wd.find_element(by, value)
    log("     Found elem by %s: %s" % (by, value))
    return elem

def find_elements(wd, by, value):
    log("Finding elements by %s: %s" % (by, value))
    elements = wd.find_elements(by, value)
    log("     Found %s elements by %s: %s" % (len(elements), by, value))
    return elements

base_url = "http://exac.broadinstitute.org/"
with open('gene_id.tsv') as csvfile:
    gene_ids = csv.reader(csvfile, delimiter= ' ')
    print gene_ids
    for row in gene_ids:
        gene_id = ', '.join(row)
        url_get(wd, os.path.join(base_url, "gene/" + gene_id))
        print(os.path.join(base_url, "gene/" + gene_id))
        url_get(wd, os.path.join(base_url, "gene/" + gene_id))
        try:
            button_elem = find_element(wd, By.XPATH, "//button[contains(@id, 'consequence_lof_variant_button')]")
            button_elem.click()
        except Exception as err:
            continue
        try:
            button_elem = find_element(wd, By.XPATH, "//a[contains(@id, 'export_to_csv')]")
            print button_elem
            button_elem.click()
        except Exception as e:
            continue
